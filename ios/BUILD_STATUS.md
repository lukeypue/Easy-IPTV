# RYZOD Apple preview build status

Verified on 2026-10-23. Native preview 0.2.0/build 2, guide/playback alignment with Android RYZOD v4.80/build 104.

- Repository: lukeypue/Easy-IPTV; branch: feature/ryzod-apple.
- Tested code commit: 2640abdeb18f434850e6c23ff43cfe738d1e88c7.
- Successful Mac workflow: https://github.com/lukeypue/Easy-IPTV/actions/runs/40015263562 (job 120625595340).
- Unsigned Release device build succeeded for the universal iPhone/iPad application.
- iPhone SE (3rd generation) / iOS 26.2 simulator build and tests succeeded.
- iPad mini (A17 Pro) / iOS 26.2 simulator login checks succeeded.

## Verification

| Suite | Test executions | Failures |
| --- | ---: | ---: |
| Swift provider/parser/guide and buffer core | 21 | 0 |
| App session, live-buffer settings, account cancellation, guide retention/concurrency and ATS policy | 7 | 0 |
| iPhone login, validation and keyboard dismissal | 2 | 0 |
| iPad login, validation and keyboard dismissal | 2 | 0 |
| Total | 32 | 0 |

Phone and iPad login UI tests passed and saved screenshot attachments. The previously inspected login layout remains covered by these tests; authenticated guide layout and physical playback are not verified by login screenshots.

Regression tests reproduced the original failures before fixes. Fresh whole-branch review and focused fix follow-up found no remaining material blocker in the reviewed code. Review fixes cover HTTP ATS policy, clearing previous account playback, refreshing cached/selected guides beyond channel 20, stable playlist favorites and rejection of non-XMLTV XML. Further checks cover late old-account responses and at most three concurrent guide requests.

## Artifacts

The successful workflow stores:

- RYZOD-Apple-0.2-unsigned-and-simulator: Release-iphoneos/Ryzod.app and Debug-iphonesimulator/Ryzod.app (7,640,998-byte ZIP; artifact 12528732296).
- RYZOD-Apple-build-results: Xcode logs, test result bundles and saved screenshots (2,590,252-byte ZIP; artifact 12528702365).

Artifacts are build evidence. The unsigned device app cannot be installed by opening a link on the iPhone. There is no signed IPA, TestFlight invitation or App Store submission yet.

## Remaining prerequisites and device checks

The owner has Windows and ordered an iPhone SE 3. TestFlight with hosted Mac signing avoids buying additional Apple hardware. Apple Developer Program membership, app registration, secure distribution signing and an App Store Connect upload remain required; none were supplied or configured in this session. See INSTALL_ON_IPHONE.md.

Actual provider login/playback, codec support, Wi-Fi recovery, prolonged playback and battery behavior need the physical phone and an authorized media source. Apple-native HLS playback does not imply Android FFmpeg compatibility. Provider date strings without offsets currently use the device timezone. Authenticated guide/catalog screen layouts have not been covered by UI automation.

This preview does not include local DVR/timeshift, scheduled recording, downloads, USB storage or Apple TV. Android application/release source was not modified.

The final documentation commit contains no executable code changes and records this exact tested code commit. Inherited invalid Android release workflows were disabled on this native branch; the Apple workflow above passed. See UPDATE_0.2.0.md for buffer semantics and platform limits.
