# RYZOD 4.82 verified release

Release: [v4.82](https://github.com/lukeypue/Easy-IPTV/releases/tag/v4.82)  
Release/source commit: `d596f1e0a4381e17894fee1493605004bd18a052`  
Successful workflow: [41150401731](https://github.com/lukeypue/Easy-IPTV/actions/runs/41150401731)  
Updater promotion on main: `13b9f6ba7658252884a9cd763a1c6a5cc650d258`

## Delivered behavior

- TV keyboard occupies about 60% of the screen; phone keys retain usable width. Dark keys, cream lettering and warm orange/gold focus improve contrast.
- Navigation cancels a pending remote confirmation. An orphan release cannot type a newly highlighted letter or activate a newly focused search control.
- Search retains its last query, bounded catalog results and vertical scroll position across playback and series navigation. Heavy indexes can be reclaimed while playing.
- The private, source-isolated guide cache survives process restarts. Valid rows remain visible during refresh and refresh failures. Resume and expiry checks run while Live browsing is resumed, and stop during playback/backgrounding.
- Smooth has an independent switch and explanation. It preserves manual buffering choices and adds no startup/recovery wait, mini guide or buffering message.

Existing seasons-first navigation and phone guide-menu regressions remain in the full suites.

## Validation

| Gate | Result |
| --- | --- |
| Full Android suite | 231 passed; no failures/errors/skips |
| Full Fire TV suite | 231 passed; no failures/errors/skips |
| Amazon distribution/launcher suite | 17 passed; no failures/errors/skips |
| Play distribution/launcher suite | 17 passed; no failures/errors/skips |
| Publisher/signature regressions | 58 passed, including real JDK signature fixtures |
| Live/guide contracts | 14 passed |
| Lint and minified signed builds | Passed in CI |
| Independent code review | Findings resolved and follow-up review completed |

TV and 360dp phone keyboard screenshots were inspected locally. The timing regression passed three fresh local test processes with its original timing assertions retained. Cold asynchronous startup remains covered by other UI tests.

The APK verifier supports Build Tools 35 numbered labels and Build Tools 37 scheme labels. It requires one signer and checks every APK certificate against the unchanged permanent pin.

## Public artifact verification

Both public updater manifests at the pinned main commit above are identical: version 4.82/code 106, with the URLs and digests below. GitHub's latest stable release is v4.82.

| Public asset | SHA-256 |
| --- | --- |
| RYZOD-v4.82.apk | `5bddd4d86942aaaf0324497a1492405b82f92938c735f0fdb6fe9c2cb03dd787` |
| RYZOD-v4.82-FireTV.apk | `5773711c94b2daf60c7d70c2c293a234382d80873632e3858405390a4096119b` |
| RYZOD-v4.82-source.zip | `462ed2b1b3a5c239937f811f7ba2d596731d44a752306b6b5d85f01194fc849f` |

Both downloaded APKs independently passed package `com.easyiptv.player`, version 4.82/code 106, target SDK 36, signature, permanent-certificate and ZIP alignment checks. Each contains eight native libraries whose ELF load segments meet 16 KB alignment.

Permanent certificate SHA-256: `8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165`.

The downloaded source ZIP matches `TZ=UTC git archive --format=zip d596f1e0a4381e17894fee1493605004bd18a052` byte for byte. UTC matters because ZIP entry timestamps otherwise follow the local archive timezone. Subsequent documentation-only commits do not change these released binaries.

## Remaining device checks

Physical Android/Fire TV readability, remote behavior and playback with real providers still require device validation before launch. Amazon/Play candidates are workflow artifacts only; no store submission or acceptance is claimed.

Provider congestion can cause buffering, but viewer count alone does not establish the cause. A larger reserve helps only when incoming video can replenish it; Smooth cannot increase provider or network throughput. See [RFC 9317](https://www.rfc-editor.org/rfc/rfc9317.html).
