# RYZOD Apple preview build status

Verified on 2026-10-01. Native preview 0.1.0, baseline Android RYZOD v4.70/build 94.

- Repository: lukeypue/Easy-IPTV; branch: feature/ryzod-apple.
- Tested code commit: 8f07745c17edb56dd26747caaec04af937036eae.
- Successful Mac workflow: https://github.com/lukeypue/Easy-IPTV/actions/runs/36821766630 (job 110238674352).
- Unsigned Release device build succeeded for the universal iPhone/iPad application.
- iPhone SE (3rd generation) / iOS 26.2 simulator build and tests succeeded.
- iPad mini (A17 Pro) / iOS 26.2 simulator login checks succeeded.

## Verification

| Suite | Test executions | Failures |
| --- | ---: | ---: |
| Swift provider/parser/guide core | 18 | 0 |
| App session, account cancellation, guide retention/concurrency and ATS policy | 6 | 0 |
| iPhone login, validation and keyboard dismissal | 2 | 0 |
| iPad login, validation and keyboard dismissal | 2 | 0 |
| Total | 28 | 0 |

Four saved native login screenshots (provider/playlist on phone and iPad) were visually inspected. The phone's form scrolls to show the explanatory footer; fields and Connect are accessible. These screenshots verify login layout, not authenticated guide layout or physical playback.

Regression tests reproduced the original failures before fixes. Fresh whole-branch review and focused fix follow-up found no remaining material blocker in the reviewed code. Review fixes cover HTTP ATS policy, clearing previous account playback, refreshing cached/selected guides beyond channel 20, stable playlist favorites and rejection of non-XMLTV XML. Further checks cover late old-account responses and at most three concurrent guide requests.

## Artifacts

The successful workflow stores:

- RYZOD-Apple-0.1-unsigned-and-simulator: Release-iphoneos/Ryzod.app and Debug-iphonesimulator/Ryzod.app (7,559,602-byte ZIP).
- RYZOD-Apple-build-results: Xcode logs, test result bundles and saved screenshots (2,575,586-byte ZIP).

Artifacts are build evidence. The unsigned device app cannot be installed by opening a link on the iPhone. There is no signed IPA, TestFlight invitation or App Store submission yet.

## Remaining prerequisites and device checks

The owner has Windows and ordered an iPhone SE 3. TestFlight with hosted Mac signing avoids buying additional Apple hardware. Apple Developer Program membership, app registration, secure distribution signing and an App Store Connect upload remain required; none were supplied or configured in this session. See INSTALL_ON_IPHONE.md.

Actual provider login/playback, codec support, Wi-Fi recovery, prolonged playback and battery behavior need the physical phone and an authorized media source. Apple-native HLS playback does not imply Android FFmpeg compatibility. Provider date strings without offsets currently use the device timezone. Authenticated guide/catalog screen layouts have not been covered by UI automation.

This first preview does not include local DVR/timeshift, scheduled recording, downloads, USB storage or Apple TV. Android application/release source was not modified.

The final documentation commit contains no executable code changes and records this exact tested code commit. Existing unrelated Android release workflow failures are inherited from the baseline; the Apple workflow above passed.
