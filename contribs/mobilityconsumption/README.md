# Mobility consumption

Mobility consumption measures the road space-time a vehicle consumes, in kilometre-hours, following
Bliemer, Loder and Zheng (2024), "A novel mobility consumption theory for road user charging",
Transportation Research Part B 189, and its application to MATSim in Ai, Fourie, Bliemer and Rashidi
(TRB Annual Meeting 2027, TRBAM-27-01254). Per link traversal it is `lambda * T + tau * D`, with
`T` the time on the link, `D` the distance, `lambda` the road space one vehicle occupies (4.87 m length
plus 6.25 m spacing) and `tau` the reaction time (1.23 s). The part of it caused by travel slower than
free flow, `max(lambda * (T - T0), 0)`, is the excess consumption. Dividing consumption by the road
space-time the network supplies (length × lanes × time, the mobility production) gives a utilisation
ratio per link and time bin; excess over consumption gives an excess ratio.

This contrib computes the metric for car traffic from the events a MATSim run already produces, as
standard per-iteration outputs, as a post-hoc analysis of any events file, and as SimWrapper
dashboards with time-animated link maps.

Status: under construction. See `PLAN.md` for the design and `QUALITY.md` for the quality gate.
