# Mobility consumption contrib: implementation plan

Status: implemented 2026-09-10 (phases 0 to 6 below), on branch `mobility-consumption`. What was
verified and what was not is recorded in `README.md` under "Known limitations". Branch `mobility-consumption` in `~/matsim`, based on
`upstream/main` (matsim-org/matsim-libs, 2026-09-09). The `langkoos/matsim` fork's own `master` was
18 months behind upstream and is not used as a base.

## 1. What is being built

A MATSim contrib `contribs/mobilityconsumption` (package `org.matsim.contrib.mobilityconsumption`)
that computes the mobility consumption metric of Bliemer, Loder and Zheng (2024), as operationalised
in the TRB 2027 submission (Ai, Fourie, Bliemer, Rashidi), for car traffic, and reports it as

- standard per-iteration and end-of-run outputs of a Controler run,
- a post-hoc analysis command that works on any existing run's events file,
- SimWrapper dashboard pages, including two time-animated link maps,

with sample size handled explicitly so results at 1%, 10% and 100% are comparable.

Scope of this first stage is flowing car traffic only. Transit, a per-person or ridership-weighted
variant, and on-street parking follow later; the data model below leaves the seams for them.

## 2. Metric, as implemented

Per traversal segment i of a vehicle on a link:

- MC_i = λ·T_i + τ·D_i, with T_i time on the link (s), D_i distance (m).
- MC_excess_i = max(λ·(T_i − T0_i), 0), T0_i the free-flow time for the same segment.
- λ = vehicleLength + vehicleSpacing = 4.87 + 6.25 = 11.12 m, τ = 1.23 s (paper defaults).
- Stored internally in metre-seconds, reported in km·h (÷ 3.6e6).

Per link l and time bin t (900 s default): MC_l,t and MC_excess_l,t from segment shares that fall
into the bin, splitting each segment across bins under uniform speed on the link (paper eq. 8).
Mobility production MP_l,t = length_l · lanes_l(t) · Δt, using time-variant lanes where present.
Utilisation U_l,t = MC_l,t / MP_l,t; excess ratio E_l,t = MC_excess_l,t / MC_l,t (defined when MC > 0).
Daily per link and network totals are sums of the bins; network MP = Σ_l length·lanes·H over the
analysis window (default 24 h, configurable).

Event semantics (verified in QSim source):

- Departure link: `VehicleEntersTrafficEvent` at relative position 1.0, then `LinkLeaveEvent`. The
  vehicle sits in the link's buffer, distance 0, time = buffer wait, T0 = 0. Recorded as a DEPARTURE
  segment: all of its MC is excess. Included by default; switch `includeDepartureSegments`.
- Full traversal: `LinkEnterEvent` to `LinkLeaveEvent`. T0 = floor(L / min(freespeed(t), vmax)) + 1 s,
  matching the QSim convention used by `QSimFreeSpeedTravelTime` (the extra second is the node move).
- Arrival link: `LinkEnterEvent` to `VehicleLeavesTrafficEvent` (position 1.0, so full length). T0
  without the node second, since arrival fires at the earliest exit time. ARRIVAL segment, included
  by default; switch `includeArrivalSegments`.
- `VehicleAbortsEvent` closes an open segment without recording it; counted in a diagnostics line.
- Mode from `VehicleEntersTrafficEvent.getNetworkMode()`; default set {car}. Transit vehicles
  (seen via `TransitDriverStartsEvent`) excluded by default.
- Vehicle length source: `fixed` (default, paper λ) or `vehicleType`, which uses
  `VehicleType.getLength()` as λ directly (MATSim's 7.5 m default already includes spacing).
  Vehicle lookup via `VehicleUtils.findVehicle` (allvehicles element online, `output_allVehicles`
  offline).

Sample size: MC is upscaled by 1/sampleSize everywhere; MP is never scaled. `sampleSize` is a config
parameter; when unset it resolves to `simwrapper.sampleSize` if that module is present, else
`qsim.flowCapacityFactor`, and the resolved value is logged and written into the stats file. The
post-hoc command takes `--sample-size` through the standard `SampleOptions`, which SimWrapper fills
automatically. Invariants tested: scaling every vehicle's contribution by 1/s leaves E unchanged and
scales U and MC by 1/s; the paper's discretisation caveat for small samples is documented, not hidden.

## 3. Entry points

| Use | Entry point |
| --- | --- |
| In-simulation | `MobilityConsumptionConfigGroup` (`mobilityConsumption` module in config.xml) plus `controler.addOverridingModule(new MobilityConsumptionModule())` |
| Example runner | `RunMobilityConsumption` (config path as argument) |
| Post hoc | `MobilityConsumptionAnalysis`, a `MATSimAppCommand` (`@CommandSpec(requireEvents, requireNetwork, produces=...)`, group `mobilityconsumption`) runnable from the CLI on a run directory |
| Dashboards | `MobilityConsumptionDashboardProvider` registered by Java SPI in `META-INF/services/org.matsim.simwrapper.DashboardProvider`, active when the config module is present; `CreateMobilityConsumptionDashboard` CLI to append the dashboard to an existing run |

Dependencies: `matsim` core, `contribs/application` (analysis options, `CreateAvroNetwork`, XYT avro),
`contribs/simwrapper`. Registered in `contribs/pom.xml` and the table in `contribs/README.md`.

## 4. Package layout

```
org.matsim.contrib.mobilityconsumption
  MobilityConsumptionConfigGroup      ReflectiveConfigGroup, @Parameter/@Comment
  MobilityConsumptionModule           binds handler + controller listener
  MobilityConsumptionParameters       immutable record resolved from config (λ, τ, bins, window, sample)
  RunMobilityConsumption              example main; body delegates to a tested prepare()
  core/
    TraversalSegment                  record: link, vehicle, driver, mode, enter, leave, distance, freeFlowTime, kind
    TraversalSegmentCollector         event handler -> segments (departure/full/arrival/abort logic)
    MobilityConsumptionCalculator     segment -> (MC, excess); bin splitting
    MobilityProduction                MP per link-bin from Network (time-variant lanes)
    MobilityConsumptionAccumulator    per link-bin, per link daily, network per bin, per mode, diagnostics
    ConsumptionSource                 enum FLOW (PARKING, TRANSIT reserved)
  io/
    MobilityConsumptionWriter         CSV writers, one per table
    MobilityConsumptionControllerListener  iteration/shutdown wiring
  analysis/
    MobilityConsumptionAnalysis       MATSimAppCommand producing the tables + wide CSVs + optional XYT avro
  dashboard/
    MobilityConsumptionDashboard, MobilityConsumptionDashboardProvider, CreateMobilityConsumptionDashboard
```

Seams for later stages: `ConsumptionSource` on every record and output row; `MobilityProduction` behind a
small interface so kerb supply can be added; segments carry the driver id so a per-person variant is a
grouping, not a rewrite; λ per vehicle type already supported.

## 5. Outputs

Controller run, in iterations where `iteration % writeInterval == 0` or last (writeInterval default 10):

- `ITERS/it.N/N.mobilityConsumption_links.csv.gz`: link_id, length, lanes, mc_kmh, mc_excess_kmh, mp_kmh, utilization, excess_ratio, vehicle_km, vehicle_h, segments
- `ITERS/it.N/N.mobilityConsumption_links_bins.csv.gz`: link_id, bin_start, bin_end, mc_kmh, mc_excess_kmh, mp_kmh, utilization, excess_ratio (long format)
- `ITERS/it.N/N.mobilityConsumption_network_bins.csv`: bin, mc, mc_excess, mp, utilization, excess_ratio, per mode
- optional `N.mobilityConsumption_segments.csv.gz` (`writeSegments=true`): vehicle, driver, link, kind, enter, leave, distance, mc, excess

Every iteration, run root: `mobilityConsumption_stats.csv` (iteration, mc_kmh, mc_excess_kmh, excess_ratio,
mp_kmh, mc_mp_ratio, vehicle_km, vehicle_h, segments, aborted, sample_size), the analogue of scorestats.

Shutdown: `output_mobilityConsumption_*` copies of the last written iteration plus the stats file.

Post-hoc command, into `analysis/mobilityconsumption/`: the same four tables as `mc_links_daily.csv`,
`mc_links_bins.csv`, `mc_network_bins.csv`, `mc_stats.csv`, plus wide tables for the animated link map
(`mc_links_utilization_wide.csv`, `mc_links_excess_ratio_wide.csv`: link_id then one column per bin
labelled HH:MM) and, with `--grid-size`, `mc_grid_bins.avro` (XYT raster of MC per cell per bin, link MC
distributed along link geometry).

## 6. SimWrapper dashboard

Rows, top to bottom:

1. Tiles from `mc_stats.csv`: total MC, excess MC, excess ratio, MC/MP, vehicle-km, vehicle-h.
2. Plotly lines: network U and E by time bin; and, when the run wrote it, the per-iteration series
   from `mobilityConsumption_stats.csv`.
3. MapPlot (static): network.avro joined on link_id; colour = daily excess ratio, width = daily MC.
4. Animated link map: SimWrapper `links` plugin with `useSlider: true` over the wide utilisation CSV,
   and a second one over excess ratio. The plugin's `SelectorPanel` renders a time-of-day slider over
   the CSV columns when `useSlider` is set (verified in simwrapper source); the Java `Links` class in
   the simwrapper contrib does not expose the field yet, so this plan adds `useSlider` and
   `showDifferences` to `Links.java` with a golden-YAML test. Small, upstreamable.
5. GridMap (optional, `--grid-size`): XYT avro with `timeSelector = slider`, the same mechanism the
   Noise and Emissions dashboards use for hourly animation.
6. Plotly scatter: per-link U versus E (paper figure 5).
7. TextBlock: definition, parameters, sample-size note (via `DashboardUtils.adjustDescriptionBasedOnSampling`).

## 7. Quality gate

Copied from `martin/main:contribs/pseudosimulation` (Martin Peris's harness), adapted: profile id
`mc-quality`, label `MC`, paths, `MODULE_REL`. Stages: dependency build, spotless on changed files,
checkstyle (4 rules), spotbugs (Min/High), tests with JaCoCo floors, monotonic metrics ratchet with a
committed baseline. Pre-commit hook installed via `scripts/install-pre-commit-hook.sh`. Bash 3.2 safe.
Floors are set from measured values after the first green run and only ever go up. `QUALITY.md`
written from the template with honest requirements (Java 25, Maven 3.8+, bash 3.2).

Known limits (from the skill's own record): the gate measures exercise, not correctness. So each phase
below carries at least one end-to-end assertion on produced numbers, not only on files existing.

## 8. Phases and commits

Each phase ends with the gate green, a ratchet if metrics improved, and a conventional commit
`feat(mobilityconsumption): ...`. Pushed to `origin` (langkoos/matsim) branch `mobility-consumption`
at the end of each phase.

0. Scaffold: pom, module registration, README stub, quality gate, one trivial class and test so the
   gate has something to measure. Commit config and first baseline together.
1. Core calculator: `MobilityConsumptionParameters`, `TraversalSegment`, `MobilityConsumptionCalculator`,
   bin splitting, `MobilityProduction`, `MobilityConsumptionAccumulator`. Unit tests with hand-computed
   values (single segment, segment across three bins, excess clipping, sample-scaling invariants).
2. Event collection: `TraversalSegmentCollector` with synthetic event streams on a three-link network:
   departure, full, arrival, abort, transit exclusion, mode filter. Assert the segment list exactly.
3. Controller integration: config group, module, listener, writers, `RunMobilityConsumption`. Test on
   `equil-mixedTraffic` for 2 iterations: files exist, sum of bins equals daily, excess ≤ MC, the
   uncongested single-vehicle case yields excess 0, stats file has one row per iteration.
4. Post-hoc command: `MobilityConsumptionAnalysis` on `equil`'s shipped `output_events.xml.gz`; assert
   the same totals as the in-run listener on the same events (same calculator, two paths, one number).
   Wide CSVs and XYT avro.
5. Dashboard: `Links.useSlider`, dashboard, provider, SPI file, CLI. Golden YAML test; kelheim-based
   run in the `DashboardTests` style asserting the analysis files; visual check of the animated map by
   serving the output directory to SimWrapper in the browser pane and taking a screenshot (if local
   serving of SimWrapper is not possible in this environment, that is reported, not skipped silently).
6. Documentation: README (metric, config reference generated from the config group's comments,
   outputs, dashboard, sample size, limitations), row in `contribs/README.md`, javadoc on public API.
   Final full gate run.

## 9. Decisions taken without asking

- Base on upstream main, not the fork's stale master.
- Departure and arrival segments included by default, each switchable; documented so the paper's
  numbers can be reproduced by turning both off.
- Free-flow time follows the QSim convention, so an uncongested run reports zero excess.
- Sample size resolution order: config parameter, then simwrapper, then flowCapacityFactor.
- Links map animation via wide CSV plus `useSlider`, with GridMap as the second option, rather than
  a new SimWrapper plugin.
- The `_template` contrib is not used as a base (its parent version is stale, 0.9.0-SNAPSHOT).

## 10. Open items for later stages (not in this plan's scope)

- Transit: segments for transit vehicles with `ConsumptionSource.TRANSIT`, and a ridership-weighted
  per-person consumption (MC per occupant), needs `PersonEntersVehicleEvent` occupancy tracking.
- Parking: `MC_park = λ_park × parked duration` per link, kerb length × window as supply, from the
  occupancy series on Pieter's `agent/parking-kerbside-capacity` branch, once stable.
- Mobility-consumption-driven pricing (decongestion contrib feedback signal).
