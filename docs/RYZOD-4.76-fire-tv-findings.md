# Fire TV home artwork: device evidence and current research

## Confirmed device evidence

Luke's photos show RYZOD 4.75 (99), distribution `fire`, installed as
`com.easyiptv.player`. The device reports Amazon AFTKRT, Android 11/API 30,
build RS8182.3811N, television UI mode 4, density 320, and Home package
`com.amazon.tv.launcher` version 7311000.1. The installed app icon and banner
both render the approved full RYZOD wide image at 1672 by 941. Amazon's pinned
home tile still displays the older plain R.

The diagnostics dialog renders the installed **application icon and banner**;
these are not simply copies of an in-app logo. The photo therefore confirms
the installed resources are current while the launcher surface remains old.
The exact launcher database/cache entry is not accessible in this workspace,
so stale launcher state is a strong inference rather than a directly inspected
database finding. Changing another resource name or resizing another banner
does not have evidence behind it as a fix for that stale tile.

At 14:44 MDT Luke also confirmed the correct icon in Fire Stick's own app
Settings. At 14:53 MDT he reported clearing RYZOD cache/data, uninstalling,
and reinstalling the Fire APK, with the old home tile unchanged. This rules
out RYZOD's private cache/preferences as the cause of that old image. Amazon
Home's persistent launcher state or catalog image mapping remain hypotheses;
this workspace cannot inspect either. Do not repeat app resets as a remedy.

## Current primary-source reports

- NuvioTV issue 2347, opened June 15, 2026, documents a GitHub-sideloaded APK
  on a Fire TV Stick 4K Max, Android 11/Fire OS 8.1.7.4, density 320. The banner
  exists at the matching density, yet the home row displays the square icon
  inside a grey wide tile. The issue was closed by an inactivity bot, **not**
  because a developer shipped a verified solution.
  https://github.com/NuvioMedia/NuvioTV/issues/2347
- In a 2026 developer discussion, AFTVnews (the Downloader creator) explains
  that sideloaded app icons remain square on Fire TV. Transparent or matching
  grey backgrounds can disguise the square, but do not make it a full-width
  Appstore tile. Another participant suggests manifest banner overrides;
  RYZOD already has the icon/banner/activity/launcher declarations involved,
  and Luke's device now proves those declarations resolve the current image.
  https://www.reddit.com/r/firetvstick/comments/1skegjb/hope_do_i_make_side_loaded_apps_rectangular/
- SmartTube issue 4339 records old/missing/different icons on separate Fire
  launcher surfaces after updates. Reported non-destructive workarounds vary:
  remove the app from the pinned row, restart/power-cycle, then pin it again.
  Some users needed a full power-cycle, and others did not recover. On
  February 27, 2026 a collaborator closed the issue, attributing remaining
  behavior to Fire OS after app-side work.
  https://github.com/yuliskov/SmartTube/issues/4339
- Amazon acknowledged the sideload-update icon-loss report in February 2025,
  but that older acknowledgment does not establish a fix for launcher 7311000.1.
  https://community.amazondeveloper.com/t/app-icon-disappears/8827

## Decision

Treat stale **content** and square **shape** as separate issues. Keep the
approved artwork and installed package identity. Do not erase RYZOD data,
uninstall the app, change package names, add duplicate launcher apps, manipulate
Amazon's private cache, or promise an APK can force Appstore tile geometry.
Do not repeat the unpin/reset suggestions: this launcher does not offer Luke a separate unpin action and reinstalling did not immediately refresh the tile.
An eventual Amazon Appstore listing needs its separately submitted Fire TV
artwork; store acceptance and physical launcher results are still unverified.

At 15:05 MDT on October 16, Luke reported that Home finally displayed the
new RYZOD logo after navigating device settings. His 15:06 photo shows the
wide artwork compressed into the square center of a grey rectangular tile.
The old-content problem has therefore refreshed on this device; the remaining
shape problem is directly visible. This also matches the separate square-slot
reports above. Luke recalls an earlier update regressing a similar state.

4.76 uses the existing approved 1536-by-1536 artwork for Fire application,
activity, logo, round-icon and launcher-intent paths in default, v26 and
television resource configurations. The 1672-by-941 TV banner stays separate
and unchanged. The package identity and resource name remain stable. Phone
icons and generic Android TV behavior are unchanged. Tests compare the actual
resolved bitmap with the approved source, check banner separation and cover
launcher categories, density requests and Android versions. These checks are
release gates to prevent replacing the square Fire icon with a wide banner
again. They cannot validate Amazon's refresh timing or force full-width tiles;
physical-device verification after the update remains necessary.
