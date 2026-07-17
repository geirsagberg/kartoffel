# Recording Location Battery Estimates

Date: 2026-07-17

## Bottom line

Changing Kartoffel's sustained high-accuracy recording interval from 1 second to 5 seconds should reduce callback, CPU, and database work by roughly 80%, but it probably will **not** reduce total location power by 80%. At intervals this short, the fused provider may keep GNSS warm or continuously active. The defensible planning estimate is therefore:

| Desired interval | Fixes/hour | Tracking power relative to 1 s | Modeled tracking-only drain on a 5,000–6,000 mAh phone |
| --- | ---: | ---: | ---: |
| 1 s | 3,600 | 100% | 0.3–1.4 percentage points/hour (planning midpoint ~0.7) |
| 5 s | 720 | 70–95% | 0.2–1.3 pp/hour (midpoint ~0.55) |
| 10 s | 360 | 50–90% | 0.1–1.3 pp/hour (midpoint ~0.45) |
| 15 s | 240 | 40–85% | 0.1–1.2 pp/hour (midpoint ~0.4) |

These are **modeling ranges, not measurements**. Google does not publish a 1/5/10/15-second battery curve. Android says higher frequency and accuracy use more battery, and that `PRIORITY_HIGH_ACCURACY` can enable GPS, Wi-Fi, cellular, and other sensors and may cause significant drain ([Android battery guidance](https://developer.android.com/develop/sensors-and-location/location/battery)). AOSP's illustrative device power profile uses 10–30 mA for GNSS under different signal quality and 50 mA while acquiring; OEMs must replace these examples with device-specific measurements ([AOSP power values](https://source.android.com/docs/core/power/values#power-values)). The table assumes that GNSS dominates, while callback and storage work falls with the requested rate; its lower bounds allow some receiver duty-cycling at 10–15 seconds.

Drain is not linear because requesting a fix less often does not imply the receiver sleeps for the rest of the interval. Remaining warm can be cheaper and faster than repeatedly reacquiring satellites, fused location can use other sensors, and other apps can keep location active. Android explicitly distinguishes higher-power full GNSS tracking from chipset power optimization through duty cycling ([GNSS measurement request](https://developer.android.com/reference/android/location/GnssMeasurementRequest.Builder#setFullTracking(boolean))).

The requested interval is also not a timer guarantee. Updates may be faster or slower, although they cannot be faster than `setMinUpdateIntervalMillis()` ([LocationRequest.Builder](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder#setIntervalMillis(long))). A fair comparison must change Kartoffel's current 500 ms minimum interval too—preferably set it equal to the desired recording interval. Otherwise fixes produced for another client may still reach Kartoffel faster. Allowing a maximum update delay of at least twice the interval may save more power through batching, but hardware support is optional and batching adds UI latency.

## Screen off versus map on

The table estimates incremental tracking drain with the screen off and the recording foreground service running. Normal background apps are throttled to only a few locations per hour, so sustained 1–15-second tracking relies on Kartoffel's user-visible foreground-service model.

With the screen and map continuously on, interval choice matters much less to the whole session. AOSP's illustrative profile assigns roughly 200 mA to the display at minimum brightness plus another 100–300 mA across the brightness range, before map CPU/GPU work ([AOSP power values](https://source.android.com/docs/core/power/values#power-values)). A rough whole-device planning envelope is therefore about **5–13 pp/hour** on a 5,000–6,000 mAh phone. Moving from 1 to 5 seconds may save only a few tenths of a percentage point per hour inside that much larger display/rendering load. Measure rather than infer: Android Studio's Power Profiler exposes GPS, display, CPU, GPU, and storage rails on supported Pixel devices, while noting that readings are device-level and noisy ([Power Profiler](https://developer.android.com/studio/profile/power-profiler)).

## Route fidelity and conservative interpolation

Kartoffel uses H3 resolution 11. Its average hexagon edge is about 28.7 m; neighboring center spacing is roughly 50 m ([H3 resolution statistics](https://h3geo.org/docs/core-library/restable/)). Approximate distance traveled between samples is:

| Motion assumption | 1 s | 5 s | 10 s | 15 s |
| --- | ---: | ---: | ---: | ---: |
| Walking, 1.4 m/s | 1.4 m | 7 m | 14 m | 21 m |
| Cycling, 5 m/s | 5 m | 25 m | 50 m | 75 m |
| Driving, 15 m/s | 15 m | 75 m | 150 m | 225 m |

Thus 10–15 seconds is generally enough for walking, 5 seconds is a good walking/cycling compromise, and even 5 seconds can skip cells in a vehicle. Primitive H3 interpolation makes 10 seconds more viable for cycling, but should not broadly paint the line between sparse points. A conservative rule is:

- accept both endpoint fixes under the existing accuracy threshold and require monotonic timestamps;
- compute H3 `gridDistance`; if it is 1, add nothing, and if it is exactly 2, add every shared intermediate neighbor on an equally short path;
- never interpolate when distance is greater than 2, the time gap exceeds a small multiple of the target interval, or implied speed is implausible for the selected activity;
- store inferred coverage distinctly from measured samples, or at minimum never invent a recorded location sample.

H3 guarantees only that `gridPathCells` returns a minimal neighbor-to-neighbor path; it may not closely follow a geographic straight line ([H3 traversal API](https://h3geo.org/docs/api/traversal/)). This limited rule repairs a short missed transition and may clear two equally plausible intermediate cells, without turning a GPS jump, tunnel, pause, or service restart into broad unrealistic coverage. It makes 10 seconds credible for walking and moderate cycling; it does not make 10–15 seconds reliable for driving.

## Recommendation

Use **5 seconds as the simple default**, with the minimum interval also set to 5 seconds. It cuts delivered fixes and current per-fix database work by 5× while retaining useful walking/cycling fidelity. Add the one-missing-cell interpolation rule above if inferred coverage is acceptable.

A better later policy is activity-adaptive:

| Activity | Desired/minimum interval |
| --- | ---: |
| Still | Stop high-accuracy location after a 60 s grace period |
| Walking | 10 s |
| Running / bicycle | 5 s |
| In vehicle | 1 s |
| Unknown / transition | 5 s |

Use Google's Activity Recognition **Transition API**, which is designed around low-power sensor signals and is preferred over raw sampling for power efficiency ([Activity Recognition](https://developers.google.com/location-context/activity-recognition/)). It requires the `ACTIVITY_RECOGNITION` runtime permission on modern Android. Combine activity with reported location speed as an escalation guard:

- `STILL` with speed below 0.5 m/s for a 60-second grace period: remove the high-accuracy location request and wait for an activity-transition event;
- `WALKING` with speed below 2.5 m/s: 10 seconds;
- `RUNNING`/`ON_BICYCLE`, or speed from 2.5 to 10 m/s: 5 seconds;
- `IN_VEHICLE`, or speed at or above 10 m/s: 1 second;
- unknown, conflicting, or transitioning: 5 seconds.

Transitions are not instantaneous and behavior varies by device, so start at 5 seconds, require stable evidence before relaxing to a slower rate, but escalate immediately when either activity or speed indicates faster travel. Use hysteresis when slowing back down to prevent interval thrashing. Speed inferred only from location has a startup blind spot—the app cannot know it needs faster fixes until a slower fix arrives—so it is a guard or fallback, not the sole controller.

Stopping fixes while still offers a much larger saving than changing 1-second polling to 15-second polling, because it lets GNSS turn off rather than merely requesting it less often. Keep the low-power Activity Recognition transition subscription active; on `STILL` exit or entry into walking, cycling, running, or vehicle activity, immediately restore a 5-second request and then adapt. Capture a final accepted fix before pausing and do not interpolate across the stationary gap beyond the same conservative one-cell rule.

Before shipping a numeric battery claim, run randomized same-phone, same-route, screen-off A/B recordings for each interval for at least one hour, repeat under good and poor sky view, and compare consumed charge plus GPS/CPU/storage rails. Separately repeat with the map on at fixed brightness.
