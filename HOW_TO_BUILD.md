# Easy IPTV 2.0 — How to build your app (no coding needed)

You upload these files to a free website (GitHub), it builds the app for you,
and you download the finished APK. About 10 minutes the first time.

## Get the files ready
1. Unzip EasyIPTV.zip on your computer so you have the `EasyIPTV` folder with
   the files loose inside it.

## Make a free GitHub account (skip if you have one)
2. Go to **github.com** and click **Sign up**. Follow the prompts.

## Create a place for the project
3. Once logged in, click the **+** in the top-right corner → **New repository**.
4. Name it `easy-iptv-2`. Leave everything else as-is. Click the green
   **Create repository**.

## Upload the app files
5. On the next page, find the line of text that says "**uploading an existing
   file**" and tap that link.
6. Open your unzipped `EasyIPTV` folder, select **everything inside it**, and
   drag it all into the upload box. Wait for the file list to finish filling in.
7. Scroll down and click the green **Commit changes**.

## Let it build
8. Click the **Actions** tab along the top of the repository.
9. A build kicks off on its own — a yellow dot means it's working. Give it
   about 3–5 minutes until it turns into a **green check** (or a red X).
10. Click into that build. Scroll to the bottom to the **Artifacts** section
    and download **EasyIPTV-app**. Inside that download is `app-debug.apk`.

## Put it on your phone
11. Get `app-debug.apk` onto your Android phone — email it to yourself or drop
    it in Google Drive.
12. Tap the file. Android will warn about installing from this source; allow it.
13. Open **Easy IPTV** and add your playlist (username & password, or an M3U
    link).

## Put it on a Firestick
1. On the Firestick, install **Downloader** from the Amazon app store.
2. Settings → My Fire TV → Developer Options → turn ON
   **Install unknown apps** for Downloader.
3. Upload `app-debug.apk` somewhere with a link (Google Drive share link
   works), enter that link in Downloader, and install.
4. Easy IPTV shows up in your apps. The whole app works with the remote's
   directional pad — whatever is highlighted gets a gold outline.

## If the build fails (red X)
Click the red X → click the job → copy the red error text → paste it to Claude.
