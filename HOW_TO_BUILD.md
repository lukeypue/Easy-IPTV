# EZTV v4.21 — Build the APK with GitHub (no coding needed)

This is a complete replacement Android source project. You do **not** need any patch files.

## Easiest path
1. Unzip `EZTV_v4.21_GPT-5.6-Sol.zip` on your computer.
2. Replace the old project contents in your GitHub repository with the **contents of this EasyIPTV project folder**.
3. Commit/push to `main` or `master`.
4. In GitHub, open **Actions** → **Build Easy IPTV APK**.
5. Wait for a green build.
6. Open the run and download **EZTV-v4.21-debug**.
7. Install `app-debug.apk` on the Fire TV Stick.

The workflow uses Java 17, Gradle 8.7, Android Gradle Plugin 8.6.1 and compileSdk 35.

## If the build is red
Open **Build debug APK** and send the first red Kotlin/Gradle error (with the file and line number). Do not randomly change Gradle or Media3 first.
