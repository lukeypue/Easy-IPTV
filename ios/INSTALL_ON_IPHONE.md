# Installing RYZOD on your test iPhone

Your iPhone SE (3rd generation, 2022) can use Wi-Fi without a SIM card or cellular plan.

## Your Windows setup

Your ordered iPhone SE and Windows PC are sufficient for testing with TestFlight. Do not buy a Mac or another iPad for that purpose. RYZOD's unsigned app has compiled and passed simulator checks; it still needs signing before it can be installed on your phone.

The next prerequisite is an Apple Developer Program publishing account for RYZOD. You can start enrollment from your Windows browser at https://developer.apple.com/programs/enroll/ using your own Apple Account. Apple currently lists $99 per year in the US. Enrollment and any identity checks are completed by the account owner.

Once membership is active, configure the app identifier, distribution signing and App Store Connect record. A hosted Mac build can then sign and upload the beta; you can use Windows for account and TestFlight administration. There is no need to own a Mac for this route. We have not configured signing credentials or uploaded a beta yet.

## Recommended: TestFlight

When RYZOD has a signed beta uploaded to Apple's TestFlight:

1. Turn on the iPhone and follow its setup screens. Connect to your home Wi-Fi and sign in with your own Apple Account.
2. Open Settings > General > Software Update and install an available supported update.
3. Open the App Store, search for **TestFlight**, and install Apple's free TestFlight app.
4. Open the RYZOD beta invitation on the iPhone. Tap **View in TestFlight** or **Start Testing**, then **Accept** and **Install**, depending on the invitation screen.
5. Open RYZOD and enter your own media provider details or playlist link.
6. Later beta updates arrive through TestFlight. Keep TestFlight installed. If a beta expires, install a newer build.

You do not need to buy a Mac or pay for a personal developer membership just to be a tester. The RYZOD publishing account does need Apple Developer Program membership and a correctly signed uploaded build. External testing may require Apple's beta review.

**Current status:** the Apple preview compiled and passed 28 test executions (18 core, 6 session, 2 iPhone UI and 2 iPad UI). There is no signed RYZOD TestFlight build or invitation yet. A GitHub unsigned .app or an Android .apk cannot be installed on your iPhone by tapping a download link.

## Alternative if you have access to a Mac

You can use your free Apple Account for personal Xcode testing:

1. Install a compatible Xcode from the Mac App Store and open it once to complete setup.
2. Open Xcode > Settings > Accounts and sign in with your Apple Account.
3. Download or clone this repository's `feature/ryzod-apple` branch. In Terminal, run the Mac build setup from README.md to generate `ios/Ryzod.xcodeproj`.
4. Open that project. Select the **Ryzod** app target > **Signing & Capabilities**. Keep automatic signing enabled and choose your **Personal Team**. If the provisional bundle identifier is unavailable, replace it with a unique identifier for your own development build.
5. Connect the iPhone to the Mac with a data-capable Lightning cable. Unlock it and accept **Trust This Computer** if asked.
6. Select the iPhone as Xcode's run destination. If asked, enable **Settings > Privacy & Security > Developer Mode**, restart, and confirm it. The setting may appear only after you connect for development.
7. Click Xcode's Run triangle. If iOS asks you to trust your developer identity, follow the prompt in **Settings > General > VPN & Device Management**. Xcode will install and open RYZOD.

Free provisioning expires after seven days; reconnect and run it from Xcode again to reinstall. You do not need a paid developer membership for this route, but you do need Mac access. A charging-only cable will not work for connecting to Xcode.

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
