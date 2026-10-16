# Fire TV launcher artwork investigation — 2026-10-16

User evidence: Downloader-installed update. Phone artwork looks correct. The 8 GB and 16 GB Fire sticks display different home tiles. Actual device model, Fire OS and launcher version are not yet known; capacity alone cannot identify a launcher implementation.

## Primary sources

- Amazon Appstore details: https://developer.amazon.com/docs/app-submission/appstore-details.html — Fire TV app icon is 1280 × 720, opaque PNG; main content safe area 882 × 448. Fire applies background treatment when artwork proportions do not match. This describes store-submitted assets, not proof of APK selection on a sideloaded home tile.
- Amazon Fire App Builder: https://developer.amazon.com/docs/fire-app-builder/ja-customize-look-and-feel.html — APK launcher icons used while testing; submitted 1280 × 720 image used in Fire TV UI. Legacy sample is end of support.
- Amazon acknowledged sideload update icon issue: https://community.amazondeveloper.com/t/app-icon-disappears/8827 — report reproduces icon loss after installing the same APK over itself on Fire OS 8.1.2.7; Amazon forwarded for investigation. Similar symptoms, not proof for this device or an established fix.
- Amazon artwork mapping issue: https://community.amazondeveloper.com/t/wrong-icons-mapping-in-fire-tv-web-apps/4171 — Amazon reproduced wrong aspect-ratio artwork in web-app settings. Different application type and surface; do not generalize it as our root cause.
- Android resource selection: https://developer.android.com/guide/topics/resources/providing-resources — television is UI mode, not storage/model; tvdpi is a distinct density; anydpi matches all densities.
- Play SDK requirement: https://developer.android.com/google/play/requirements/target-sdk — phone submissions require API 36 from August 31, 2026, with separate TV exception.
- AGP compatibility: https://developer.android.com/build/releases/agp-8-10-0-release-notes — API 36 supported; Gradle 8.11.1, JDK 17, build tools 35.
- Android 15 FGS: https://developer.android.com/about/versions/15/behavior-changes-15 — shared six-hour dataSync background budget, onTimeout must stop promptly; boot receiver cannot directly start dataSync. Existing boot receiver only rearms exact alarms.
- Android 16 changes: https://developer.android.com/about/versions/16/behavior-changes-16 — mandatory edge-to-edge and predictive-back migration. Existing Compose BackHandlers use AndroidX, and existing Compose BackHandlers already use AndroidX callbacks.

## Findings and next evidence

4.74 embeds the complete square artwork and approved wide TV artwork. Android PackageManager tests prove selection for the tested configuration, not Amazon home-screen database contents. The photographed plain R corresponds to an older drawable replaced in 4.74. There is no evidence that creating still another icon resource name clears Amazon's tile database.

Keep the approved assets and application identity. Extend density/API/launcher-category coverage. Add read-only diagnostics for actual device model/OS, density/UI mode, default home package/version, installed app version, resource paths and rendered bitmap checksums for app/activity and both launcher intent categories. Compare the installed resource output to the wrong small tile and correct larger tile before considering a resource redesign. Do not clear app data or uninstall as an icon fix.

Store listing artwork must be submitted separately through the developer console. This workspace has no authorized Amazon console session. Existing 1672 × 941 wide asset is not an exact 1280 × 720 store-upload deliverable.

## Packaging response to the two-device evidence

The cousin’s 8 GB device shows the newer logo squeezed; the user’s 16 GB device still shows the old logo. Treat aspect-ratio selection and stale imagery separately. The Fire/ Amazon distribution now overrides the *default and v26* icon resources with the wide approved asset. This means external resource loaders using a non-television configuration also get 16:9 artwork; phone/direct and Play distributions retain the square default. This is a controlled compatibility change, not a confirmed proprietary-launcher-cache fix. The small tile must still be verified on both affected devices.

## Test runtime

Official Robolectric compatibility table (https://robolectric.org/compatibility_table/) lists 4.16 for SDK 23–36 and requires Java 21 for API 36. Upgrade test framework to 4.16.1 and CI runtime to JDK 21 while keeping Java/Kotlin bytecode target 17. The saved-item menu tests pass in the isolated modern-framework run; broader API 36 checks must run with JDK 21.
