# Mobility consumption

Mobility consumption measures the road space-time a vehicle consumes, in kilometre-hours, following
Bliemer, Loder and Zheng (2024), "A novel mobility consumption theory for road user charging",
*Transportation Research Part B* 189, and its application to MATSim in Ai, Fourie, Bliemer and Rashidi
(TRB Annual Meeting 2027, TRBAM-27-01254). This contrib computes it for car traffic from the events a
MATSim run already produces and reports it as standard outputs, as a post-hoc analysis of any events
file, and as a SimWrapper dashboard with time-animated link maps.

## The metric

For one vehicle on one link (a traversal segment):

- consumption `MC = lambda * T + tau * D`, with `T` the time on the link (s), `D` the distance (m),
  `lambda` the road space one vehicle occupies (vehicle length 4.87 m plus spacing 6.25 m) and
  `tau` the reaction time (1.23 s);
- excess consumption `MC_excess = max(lambda * (T - T0), 0)`, with `T0` the free-flow time of the same
  segment, so excess is the part caused by travelling slower than free flow.

Per link and time bin (15 minutes by default) the segments' shares are summed, splitting a segment across
bins under uniform speed on the link. The road space-time the network supplies, the mobility production,
is `MP = length * lanes(t) * bin length`, using time-variant lanes where the network has them. Two ratios
follow: utilisation `U = MC / MP` and excess ratio `E = MC_excess / MC`. Daily and network values are sums.

Internally values are metre-seconds; every output is in kilometre-hours (divide by 3.6 million).

### Event semantics

MATSim's queue simulation emits, for a leg over links l0, l1, l2: vehicle-enters-traffic on l0 at
relative position 1.0, link-leave l0, link-enter l1, link-leave l1, link-enter l2, vehicle-leaves-traffic
on l2. The contrib records three kinds of segment:

| kind | events | distance | free-flow time |
| --- | --- | --- | --- |
| `DEPARTURE` | enters traffic to first link-leave | 0 (the vehicle waits in the link's buffer) | 0, so all of it is excess |
| `FULL` | link-enter to link-leave | link length | `floor(L / min(freespeed(t), vmax))` plus one time step for the node move |
| `ARRIVAL` | link-enter to leaves traffic | link length × relative position | `floor(L / min(freespeed(t), vmax))`, no node step |

This mirrors the QSim, so an uncongested run reports zero excess. `DEPARTURE` and `ARRIVAL` segments can be
switched off; with both off only link-to-link traversals count, as in the paper. Vehicles that abort are
dropped from their open segment and counted in the diagnostics. Transit vehicles are excluded by default.

### Sample size

Consumption is scaled up by the inverse of the sample size everywhere; production is never scaled. The
sample size is, in order: the `sampleSize` parameter of this module; `simwrapper.sampleSize` when that
module is present; `qsim.flowCapacityFactor`. The post-hoc command takes `--sample-size`, which SimWrapper
fills in automatically. Small samples discretise link-time bins coarsely (one vehicle stands for many);
compare utilisation and excess ratio across sample sizes with that in mind.

## Using it

In a run:

```java
Config config = ConfigUtils.loadConfig(path, new MobilityConsumptionConfigGroup());
Controler controler = new Controler(ScenarioUtils.loadScenario(config));
controler.addOverridingModule(new MobilityConsumptionModule());
controler.run();
```

or `RunMobilityConsumption config.xml`. The config module is optional; defaults apply when it is absent:

```xml
<module name="mobilityConsumption">
    <param name="timeBinSize" value="900"/>
    <param name="networkModes" value="car"/>
    <param name="sampleSize" value="0.1"/>
    <param name="writeInterval" value="10"/>
</module>
```

Post hoc, on any run directory or events file:

```bash
java -cp ... org.matsim.contrib.mobilityconsumption.analysis.MobilityConsumptionAnalysis \
  --events output_events.xml.gz --network output_network.xml.gz --sample-size 0.1 \
  --output-mc-links-daily analysis/mobilityconsumption/mc_links_daily.csv ...
```

Dashboard for a finished run, with or without the module having been installed:

```bash
java -cp ... org.matsim.contrib.mobilityconsumption.dashboard.CreateMobilityConsumptionDashboard \
  --sample-size 0.1 /path/to/run
```

With the `simwrapper` contrib installed in the run, the dashboard is added automatically whenever the
config contains the `mobilityConsumption` module (Java SPI, no further setup).

### Configuration

| parameter | default | meaning |
| --- | --- | --- |
| `vehicleLength` | 4.87 | average vehicle length, m |
| `vehicleSpacing` | 6.25 | average bumper-to-bumper spacing, m; `lambda` is the sum |
| `reactionTime` | 1.23 | `tau`, s |
| `timeBinSize` | 900 | bin width, s |
| `analysisStart`, `analysisEnd` | 0, 86400 | window over which production is supplied; consumption outside it goes to an `outside` bin |
| `sampleSize` | unset | share of the population simulated; see above for the fallback |
| `networkModes` | car | modes, as reported by the vehicle-enters-traffic event |
| `excludeTransitVehicles` | true | drop vehicles seen in a transit-driver-starts event |
| `includeDepartureSegments` | true | count buffer waiting on the departure link |
| `includeArrivalSegments` | true | count the traversal of the arrival link |
| `vehicleLengthSource` | fixed | `fixed`: `lambda` from the two parameters; `vehicleType`: `VehicleType.getLength()` (MATSim's 7.5 m default already includes spacing) |
| `writeInterval` | 10 | write the per-link and per-bin tables every n iterations and in the last; the stats row is written every iteration |
| `writeSegments` | false | also dump every segment in written iterations |

The post-hoc command exposes the same settings as options (`--time-bin-size`, `--modes`,
`--departure-segments false`, `--vehicle-length-source vehicleType --vehicles vehicles.xml`, ...), plus
`--shp` to restrict links to a shape and `--grid-size` for the raster (0 disables it).

## Outputs

Controller run, in written iterations (`ITERS/it.N/N.` prefix) and as `output_` copies at the end:

| file | content |
| --- | --- |
| `mobilityConsumption_links.csv.gz` | per link and day: `mc_kmh`, `mc_excess_kmh`, `mp_kmh`, `utilization`, `excess_ratio`, `vehicle_km`, `vehicle_h`, `segments` |
| `mobilityConsumption_links_bins.csv.gz` | the same per link and bin, long format, with `bin`, `bin_start` and `time` (HH:MM) |
| `mobilityConsumption_network_bins.csv` | network totals per bin, for `all` modes and per mode |
| `mobilityConsumption_segments.csv.gz` | optional: vehicle, driver, link, kind, times, consumption per segment |
| `mobilityConsumption_stats.csv` | one row per iteration: totals, ratios, sample size, aborted segments (run root, like scorestats) |

Post-hoc command and dashboard, in `analysis/mobilityconsumption/`: `mc_links_daily.csv`,
`mc_links_bins.csv`, `mc_network_bins.csv`, `mc_stats.csv`, `mc_tiles.csv`, the wide tables
`mc_links_utilization_wide.csv`, `mc_links_excess_ratio_wide.csv` and `mc_links_mc_wide.csv` (one row per
link, one column per bin, the layout SimWrapper's link map animates) and `mc_grid_bins.avro` (an XYT
raster of consumption per cell and bin for the grid map).

## Dashboard

Tiles with the daily totals; utilisation and excess ratio by time of day; consumption over iterations;
a daily link map (width consumption, colour excess ratio); two link maps with a time slider, over the
utilisation and excess-ratio wide tables; a grid map with a time slider; and the per-link utilisation
versus excess scatter of the paper's figure 5.

## Known limitations

- Small samples discretise heavily: one simulated vehicle stands for `1 / sampleSize` vehicles, so a
  single departure-buffer wait on a short link can push that link's utilisation far above 1 in a bin.
  Judge utilisation and excess ratio at link level with the sample size in mind, as the paper advises;
  network-level values are robust.
- SimWrapper's `links` plugin (the time-animated maps) parses the link id column as 32-bit floats, so
  networks with large numeric link ids (above about 16 million, as in the Kelheim example) are matched
  to the wrong rows. Networks with small or non-numeric ids are unaffected. The daily `map` panel joins
  ids as strings and is not affected. Worth reporting to SimWrapper; until it is fixed, use the grid
  map and the daily map for such networks.
- The dashboard was checked in SimWrapper 4.3.9 served locally: tiles, time-of-day charts, the daily
  link map, the grid map with its time slider and the scatter render; the two animated link maps load
  their data and slider but drew no lines in that build (WebGL buffer errors in the plugin), so they
  remain unverified visually.

## Development

The module is gated: see `QUALITY.md`. Run `scripts/quality.sh` before committing; the pre-commit hook
does so for you once installed. `PLAN.md` records the design and the stages still to come: transit and a
per-person variant, then on-street parking as a consumption source with kerb length as supply.
