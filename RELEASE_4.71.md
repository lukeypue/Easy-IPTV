RYZOD 4.71 (build 95) updates Android phones and Fire TV.

- Shared alphabet, symbols and accented-letter keyboard pages follow the supplied remote keyboard references. Orange translucent keyboard surfaces, consistent D-pad wrap, case, delete and clear. Search keyboard sits at the top with results visible underneath.
- Search begins with the first character. Catalog text is prepared once and filtered on a background thread with bounded results and cancellation; guide indexing does not hold up catalog results.
- Up/Down in the guide lands on the first visible program beside the next channel. Right/Left navigates programs and pages ahead through three days. Removed Earlier/Now/Later controls; channel tiles are orange and the header identifies the day.
- Manual recording has 30-minute duration steps from 30 minutes to five hours, with the actual stop time shown before confirmation.
- Recording retries dropped/ended/stalled connections until its original stop time, appending to the saved file. Pending schedules survive their start and can recover the remaining portion after restart. Explicit Stop clears recovery. Duplicate starts cannot truncate a running recording.
- Recording preparation and asynchronous player recovery cannot resume hidden playback over another app. Completing a recording does not autoplay it.

Install over your existing RYZOD app. Keep the device powered and storage available; missed footage during a power/network outage cannot be recovered. Program listings depend on how much guide data the provider supplies.

Validation: unit, Compose/Robolectric and local HTTP regression tests; debug/release compilation. The publishing workflow checks application ID, version and permanent signing certificate before enabling both updater manifests. Physical Fire Stick behavior, real provider outages and TV standby still require device confirmation.
