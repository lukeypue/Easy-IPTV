# EZTV v4.17 — Build the APK with GitHub (no coding needed)

This ZIP already contains the GitHub Action that builds the Android APK.
You do not need Android Studio.

## Build it
1. Unzip `EZTV_v4.17_GPT-5.6-Sol.zip` on your computer.
2. Open your EZTV GitHub repository.
3. Replace the old project files with everything inside the unzipped `EasyIPTV` folder, then commit the changes.
4. Open the **Actions** tab.
5. Choose **Build Easy IPTV APK**. A push to `main`/`master` normally starts it automatically; you can also press **Run workflow**.
6. Wait for the build to show a green check.
7. Open that run and download the artifact named **EZTV-v4.17-debug**.
8. Unzip the artifact. The install file is **app-debug.apk**.

## Put it on the Fire TV Stick
Use the same sideload method you have already been using for EZTV. Install `app-debug.apk` over the prior test build so your settings remain available.

## If GitHub shows a red X
Open the failed build, expand **Build debug APK**, copy the red error text, and send that error back to the AI reviewer together with `EZTV_v4.17_GPT-5.6-Sol.zip`. Do not start changing random Gradle versions first—the exact compiler message is more useful.

## Important first test
Before changing more settings, test the same known-good and known-bad channels/shows you used with v4.16/TiviMate. See `TEST_V4.17_FIRST.md` for the short test list.
