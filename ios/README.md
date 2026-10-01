# RYZOD for iPhone and iPad

Native Apple preview, baseline RYZOD Android v4.70/build 94. Minimum target iOS 16; current Xcode and a Mac are required to compile the Apple UI.

Implemented: secure provider/playlist login, duration-based guide with selectable gaps, live preview and fullscreen player, channel selection retention, provider catalog browsing, series episodes, local debounced search, per-provider favorites and sign-out.

Apple playback uses AVPlayer. Xtream live streams use HLS (.m3u8), unlike the Android .ts default. There is no silent promise of FFmpeg-equivalent codec/container support. Provider HLS support and authorized real streams require device testing. No content is included.

Not yet included: local timeshift/DVR, manual scheduled recording, downloads, external USB storage and Apple TV. Do not treat the preview version as Android feature parity.

## Mac build

```sh
brew install xcodegen
cd ios/RyzodCore
swift test
cd ..
xcodegen generate
xcodebuild -project Ryzod.xcodeproj -scheme Ryzod -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath DerivedData CODE_SIGNING_ALLOWED=NO build
```

Open the generated Ryzod.xcodeproj in Xcode for personal-device signing. Set the application's Signing & Capabilities team to the owner's team; the declared identifier com.ryzod.player is provisional until registered in that account. Do not commit secrets.

Branch-specific CI builds on a Mac and runs parser/guide tests and native login UI tests. Its unsigned .app artifact is a compile result, not an installable IPA or TestFlight distribution.

Credentials are stored in Keychain with WhenUnlockedThisDeviceOnly access. Favorites store only provider-scoped item identifiers. Provider API requests use ephemeral sessions and a 64 MiB response cap. Network errors shown to users omit request URLs and passwords. HTTP is allowed for providers that require it; prefer HTTPS whenever the provider offers it.

The app owns one AVPlayer. Leaving live full-screen restores preview; leaving movie/episode full-screen stops playback. Stop releases the current player item. There is no automatic disk buffer.

See INSTALL_ON_IPHONE.md for the user's installation steps and PORT_PLAN.md for the remaining port milestones.
