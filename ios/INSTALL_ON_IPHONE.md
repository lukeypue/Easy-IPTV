# Installing RYZOD on your test iPhone

Your iPhone SE (3rd generation, 2022) can use Wi-Fi without a SIM card or cellular plan.

## Recommended: TestFlight

When RYZOD has a signed beta uploaded to Apple's TestFlight:

1. Turn on the iPhone and follow its setup screens. Connect to your home Wi-Fi and sign in with your own Apple Account.
2. Open Settings > General > Software Update and install an available supported update.
3. Open the App Store, search for **TestFlight**, and install Apple's free TestFlight app.
4. Open the RYZOD beta invitation on the iPhone. Tap **View in TestFlight** or **Start Testing**, then **Accept** and **Install**, depending on the invitation screen.
5. Open RYZOD and enter your own media provider details or playlist link.
6. Later beta updates arrive through TestFlight. Keep TestFlight installed. If a beta expires, install a newer build.

You do not need to buy a Mac or pay for a personal developer membership just to be a tester. The RYZOD publishing account does need Apple Developer Program membership and a correctly signed uploaded build. External testing may require Apple's beta review.

**Current status:** there is no signed RYZOD TestFlight build or invitation yet. A GitHub unsigned .app or an Android .apk cannot be installed on your iPhone by tapping a download link.

## Alternative if you have access to a Mac

A free Apple Account can run a development build through Xcode on your personal iPhone. This route requires Mac access, a compatible Xcode version, connecting the iPhone, selecting your Apple Account as the signing team, and enabling Developer Mode if Xcode requests it. Free provisioning expires after seven days and needs another installation from Xcode.

## What the publishing setup needs

- Your Apple Account and Apple Developer Program enrollment (Apple currently lists $99/year in the US).
- A registered app identifier, matching signing team, and App Store Connect app record for RYZOD.
- A Mac/Xcode build environment (local or hosted) with your signing credentials configured securely.
- A signed archive uploaded to App Store Connect, then assigned to your TestFlight tester group.

Do not send account passwords, certificate passwords, or private signing keys in chat or commit them to GitHub. Signing should be configured in the account/build service's secure settings.

## First test checklist

- Login works and the keyboard does not cover fields.
- Live channels have picture and sound; switch through several channels.
- Two-hour guide programs appear as one long block; blank guide cells still open channel options.
- Update Guide preserves the previous guide if the provider cannot respond.
- Return from full-screen live TV to the selected channel and live preview.
- Search for a title containing a slash, such as 20/20.
- Browse movie categories, series seasons and episodes; back navigation works.
- Add favorites, close/reopen the app, and check they remain.
- Test Wi-Fi interruption and retry; observe prolonged playback, audio and battery behavior.

This first Apple preview does not include local DVR, scheduled recording, offline downloads, USB recording storage or tvOS.

References: https://developer.apple.com/testflight/ and https://developer.apple.com/help/account/basics/about-your-developer-account
