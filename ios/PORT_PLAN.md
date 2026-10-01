# RYZOD Apple port plan

Status: source baseline located and Apple development branch created. No Apple app or installable beta has been built yet.

## Source baseline

- Repository: lukeypue/Easy-IPTV.
- Apple branch: feature/ryzod-apple.
- Starting release: RYZOD v4.70, Android build 94.
- Starting commit: 637c449f79db1f2d784d0561041a06be750c779b.
- Android source currently uses Kotlin, Jetpack Compose and Media3/ExoPlayer, including a software audio decoder. These Android frameworks cannot serve as the Apple application runtime.

## Requested outcome

Bring the latest RYZOD experience to iPhone and iPad, preserving its branding, provider login, Live/Movies/Series navigation, guide behavior and user-owned provider content model. Use the actual v4.70 source as the behavior reference. Keep Apple files under ios/ with an independent Apple build pipeline.

Initial target assumption: iPhone and iPad. Apple TV requires a separate tvOS target and remote-focus validation; it is a subsequent scope decision.

## Proposed implementation

Use a native SwiftUI application with a separate provider/data layer, credentials stored in Keychain, cancellable catalog/guide loading, and an Apple playback adapter. Start with AVFoundation-supported streams. Audit the existing provider URL generation and supported formats before deciding whether an additional playback library is needed; Android's FFmpeg audio support does not establish Apple codec parity.

Adapt the guide for touch and different screen widths. Carry over v4.70 rules: one cell per actual provider program duration, selectable missing-guide gaps, an Update Guide action explaining that the provider supplies guide information, retention of the old guide on refresh failure, and return to the selected channel when leaving playback.

The first device beta should contain provider login, live playback, guide, search, favorites, movies and series. Add resilient local downloads and recordings in later increments only after validating stream handling, storage, cancellation and lifecycle behavior on physical devices.

## Recording and pause behavior

The reference Android behavior starts temporary disk recording only when Pause is pressed, bounds retained history to 30 minutes and disk capacity, and releases it on channel switch, Stop or Return to Live.

Investigate that behavior independently on Apple before promising parity. iOS background execution is managed by the operating system. A local scheduled recording must not be presented as guaranteed while the app is suspended or terminated. Evaluate foreground recording and pause/resume first; reliable unattended recording may require an always-on external recorder.

Do not simulate working recording controls or advertise unverified capabilities in a test build.

## Build and beta prerequisites

An Apple build needs a macOS/Xcode environment, either local or hosted. Check availability and account access before promising an IPA or a TestFlight link.

For convenient remote installation, use TestFlight under the product owner's Apple Developer Program account. The tester only needs a compatible Apple device and Apple Account; they do not need their own paid developer membership or Mac. The publishing account needs paid membership. Free Xcode personal-device testing is a separate route with short-lived provisioning.

Keep beta builds distinct from the Android release/version manifests. Do not publish an iOS build as an Android updater asset.

## Suggested low-cost test hardware

- iPhone SE, 3rd generation (2022), 64 GB: preferred low-cost initial physical test device.
- iPad, 9th generation (2021), 64 GB, Wi-Fi: alternative with a larger guide display.
- Both appear on Apple's current iOS/iPadOS compatibility lists at the time of planning.
- Use Wi-Fi; a cellular subscription is unnecessary for this app's initial testing.
- Before purchase, confirm the exact model/generation, working battery and display, absence of Activation Lock/organization management, and a return policy.
- A single physical device is enough to start; simulator checks and later testing on both phone and tablet are needed before release.

## Validation gates

1. Pin source behavior to v4.70 and inspect provider/parser code and relevant Android regression tests.
2. Verify provider authentication, stream URL generation, playlist parsing, timezone handling, and program duration geometry with meaningful Swift tests.
3. Compile and run on Apple simulators for phone and tablet layouts.
4. Exercise authorized real provider streams on a physical device, including audio formats, prolonged playback, switching channels, reconnects and memory pressure.
5. Validate download/recording cancellation, disk limits and app lifecycle behavior before exposing those features.
6. Produce a signed device beta and verify TestFlight installation and updates.
7. Document the actual supported features and limitations before any public store release.

## References

- Apple current iPhone support: https://www.apple.com/os/ios/
- Apple current iPad support: https://www.apple.com/os/ipados/
- Xcode requirements: https://developer.apple.com/xcode/system-requirements
- Developer membership: https://developer.apple.com/programs/
- Personal testing: https://developer.apple.com/help/account/basics/about-your-developer-account
- TestFlight: https://developer.apple.com/testflight/
