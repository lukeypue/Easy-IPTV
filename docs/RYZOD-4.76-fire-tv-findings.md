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
Try the reported unpin/power-cycle/repin sequence on the affected device.
An eventual Amazon Appstore listing needs its separately submitted Fire TV
artwork; store acceptance and physical launcher results are still unverified.

This update addresses Search memory/typing behavior independently of Amazon's
launcher. No further speculative artwork rewrite is bundled with it.
