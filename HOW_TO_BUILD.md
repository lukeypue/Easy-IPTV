# EZTV v4.18 — Build the APK with GitHub (no coding needed)

The zip contains the Android source project and a ready GitHub Actions workflow.

## Easiest path
1. Unzip `EZTV_v4.18_GPT-5.6-Sol.zip` on your computer.
2. Put the **contents of the EasyIPTV folder** in your GitHub repository.
3. Commit/push to `main` or `master`.
4. In GitHub, open **Actions**.
5. Open **Build Easy IPTV APK**.
6. Wait for the green build run.
7. Open that run and download the artifact named **EZTV-v4.18-debug**.
8. Inside the artifact is `app-debug.apk` for your Fire Stick test.

The workflow uses Java 17, Gradle 8.7, Android Gradle Plugin 8.6.1 and compileSdk 35.

## If the build fails
Open the failed build, expand **Build debug APK**, copy the red compiler/error text, and send that exact text back with `EZTV_v4.18_GPT-5.6-Sol.zip`.

Do not randomly change Gradle/Media3 versions first. The compiler message tells us much more.

## Test before adding more features
Use `TEST_V4.18_FIRST.md`. In particular, test Smooth Live and AFR separately so we know which one is improving the problem channel.
