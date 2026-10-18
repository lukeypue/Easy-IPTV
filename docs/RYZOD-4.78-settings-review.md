# Settings and application review for 4.78

Baseline: the released 4.77 tree `5facb69f48495f11793f421b25eb15134366d2a2`.

## Reproduced faults

Real Compose remote-input tests reproduced OK leaving focus in the menu for Search, Settings and the current provider category. Real Media3 load-control tests reproduced loading remaining off with 20 seconds left after reaching its upper target, and Natural/Steady both using a 10-second rebuffer threshold at the user's small-buffer/10-second-lock combination. A Settings UI test reproduced the stream-buffer control saving a preference without applying the player-change callback.

## Settings review

| Setting | Application path and review |
| --- | --- |
| Provider streams | Persistent connection budget used by live, recording and download ownership checks. Keep the existing single-stream protection. |
| Storage / USB | Selected destination is used for new writes. Availability and write tests remain necessary on physical Fire OS USB hardware. |
| Download retention | Persistent per-device retention preference; no migration or deletion policy change. |
| Stream buffer | Rebuild on the next tune through the same callback as the lock and recovery settings. Do not keep an old preview player alive with old load control. |
| Channel lock-in | Distinct startup threshold. The loading reserve leaves headroom above startup and recovery thresholds. |
| Natural / Steady | Normal speed in both modes. Natural recovery is at least 8 seconds; Steady is 15–30 seconds. Do not clamp both to the small forward preference. |
| AFR: Live / VOD | Separate persistent choices are read when opening the player; actual HDMI refresh switching requires device/display verification. |
| Captions | Persistent preference and immediate application to an existing player; caption availability remains provider-dependent. |
| Watching clock | Persistent preference read by the player screen. |
| Updates | Preserve direct/Fire distribution selection, package/version/signature verification and matching universal/TV hashes. |
| Device & artwork check | Existing read-only installed artwork/device report. No new background polling was introduced by settings application. |

## Buffer limits

`StreamBufferPolicy` owns startup, rebuffer and refill durations. Minimum and maximum load-control reserve are equal, so loading resumes when reserve is consumed instead of waiting for a separate low-water mark. The reserve can exceed a small preference to leave headroom above the requested recovery cushion. The existing low-RAM 32 MiB allocation ceiling remains authoritative; available video duration varies with bitrate.

Direct playback preserves the provider URL. For HLS, request a target live offset twice the recovery threshold and normal playback speed. Media3 may constrain the target to the provider's live window. This does not create extra bandwidth or turn pause-only DVR into a rolling recording.

## Validation scope

The release pipeline runs the complete regression suite for both universal and Fire distributions, checks lint, validates the permanent signing certificate and package version, and verifies native/APK alignment before publishing matching updater manifests. Fire Stick hardware, provider interruptions, USB availability and HDMI switching must still be exercised on the device.
