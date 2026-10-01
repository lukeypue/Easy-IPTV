# RYZOD Apple Implementation Plan

> For agentic workers: execute inline with superpowers:executing-plans; use test-driven development and a final whole-branch review.

Goal: build a native iPhone/iPad first beta based on RYZOD 4.70, with real provider access and a verified simulator build.
Architecture: Foundation Swift package owns provider URLs, decoding, guide geometry and requests. SwiftUI app owns Keychain credentials, catalog state, favorites and a single AVPlayer. A branch-specific Mac workflow compiles and tests without signing secrets.
Tech Stack: Swift 5.9+, SwiftUI, Foundation, AVFoundation, Security, XcodeGen.
Spec: ios/PORT_PLAN.md.

## Global constraints
- RYZOD branding; Android behavior baseline v4.70/build 94.
- iPhone and iPad, minimum iOS 16; Apple TV is outside this first build.
- No provider content or credentials checked into source; use the user's authorized provider.
- Preserve provider program durations, clickable guide gaps, selected-channel return and previous guide on refresh failure.
- Live loads before on-demand; one player; no automatic disk buffer.
- No simulated recording/download buttons; unsupported features disclosed honestly.
- Save on feature/ryzod-apple; independent Apple workflow; no Android release-manifest changes.

## Review focus
- Credentials containing slash, plus, percent and non-ASCII must round-trip without changing request meaning.
- Inconsistent provider JSON IDs/types and wrappers must decode; login-error objects must not look like empty catalogs.
- Overlapping/cross-window guide events must cover the window without duplicate or negative-width cells.
- Cancellation/account changes must prevent stale requests from repopulating another account's screen.
- HLS versus MPEG-TS/unsupported audio must be explicitly understood; Apple-native decoding does not imply Android FFmpeg parity.

### Task 1: Provider and guide core
Files: ios/RyzodCore/Package.swift; Sources/RyzodCore/{Models,ProviderURLs,ProviderParser,GuideGeometry,ProviderClient}.swift; Tests/RyzodCoreTests/CoreTests.swift.
Interfaces: ProviderCredentials.api(action:parameters:)->URL, stream(kind:id:extension:)->URL; ProviderParser.categories/media/epg/episodes; M3UParser.parse; GuideGeometry.cells; ProviderClient.authenticate/categories/media/epg/episodes.
- [x] Write fixture-based tests for URL encoding, login, tolerant catalog parsing, M3U, guide durations/gaps, EPG timezone/base64 and episode order.
- [x] Run Swift tests on Mac against intentionally incomplete implementations; record runtime failures.
- [x] Implement the real core, including bounded requests and cancellation propagation; run the whole package suite green.
- [x] Commit the tested core.

### Task 2: Native app
Files: ios/Ryzod/{RyzodApp,AppModel,CredentialStore,PlaybackController,LoginView,RootView,LiveGuideView,CatalogView,SeriesView,PlayerView,SettingsView,Theme}.swift; Info.plist; Assets.xcassets; ios/project.yml.
Consumes Task 1 interfaces; produces a runnable RYZOD application with provider login, live guide/playback, lazy movie/series catalogs, local search, favorites and safe sign-out.
- [x] Implement secure credentials and generation-scoped catalog tasks; retain current guide on failed refresh.
- [x] Build touch screens, one shared AVPlayer, preview/fullscreen handoff, category filters, episode picker and error/retry feedback.
- [x] Add native UI smoke tests for login validation, keyboard dismissal and launch branding.
- [x] Compile and test with Xcode on Mac; resolve compiler or test failures before committing.

### Task 3: Delivery and review
Files: .github/workflows/apple.yml; ios/README.md; ios/INSTALL_ON_IPHONE.md; ios/BUILD_STATUS.md.
- [x] Mac workflow runs core tests, generates Xcode project, builds unsigned device/simulator apps, and executes UI tests; store artifacts and test results.
- [x] Review the full diff with a fresh reviewer, address material findings, rerun affected tests.
- [x] Prepare TestFlight setup and free personal-Xcode alternative instructions; clearly separate unsigned artifacts from an installable signed beta.
- [x] Verify saved branch files and workflow status; report any signing/account prerequisite accurately.

Completion evidence: ios/BUILD_STATUS.md. The native preview milestone is complete; signed installation remains dependent on the owner’s Apple publishing account and signing setup.
