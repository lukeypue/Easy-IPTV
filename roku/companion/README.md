# RYZOD companion 0.1.0

Optional computer/NAS storage for the RYZOD Roku channel. Requires Python 3.10 or newer. Install FFmpeg and make `ffmpeg` available on PATH for recordings and HLS/DASH downloads. Direct MP4/MKV/TS downloads use Python alone.

Unzip this folder, then run:

```sh
python3 server.py --bind 0.0.0.0 --port 8787 --data ./ryzod-data
```

On Windows, `py -3 server.py --bind 0.0.0.0 --port 8787 --data ./ryzod-data` also works. Allow inbound TCP 8787 on the computer's private/home-network firewall. Keep this computer powered on; reserve its LAN address in your router so pairing stays valid.

In Roku → Settings, enter `http://COMPUTER-LAN-IP:8787` and the pairing token printed by the server. Choose **Test companion connection**. Set provider streams to your actual subscription allowance (default 1).

The server persists schedules and job progress in SQLite and media under the chosen data directory. It attempts interrupted supported downloads again, using HTTP Range and source validators; servers that ignore Range cause a clean restart. HLS/DASH downloads restart rather than append byte ranges. Recording retries save the remaining time and may contain a discontinuity/gap; they cannot recover content missed while the server/provider was offline.

Roku obtains and renews a ten-second provider playback reservation while paired. Jobs count these reservations against the configured stream allowance. An upcoming recording reserves capacity fifteen seconds early; Roku stops playback on refusal or renewal failure. After a companion restart, jobs wait twelve seconds to let clients renew/stop. Stopping playback releases its reservation after Video stops. Each playback session has a unique ID, so delayed renewals cannot revive a stopped session or release a newer one. Closing/crashing the channel releases capacity after expiry. Other devices/apps using the provider are outside this protocol; account for them in the configured allowance.

Keep the service on your trusted home network. The bearer token authenticates API and media requests. The SQLite database contains provider URLs/credentials; protect the data directory and do not share the pairing token. HTTP is unencrypted on the LAN. Do not port-forward this service. Back up the data directory when the server is stopped. Ctrl+C stops the server; queued/scheduled work resumes when restarted.

Tests: `python3 -m unittest discover -s . -p 'test_*.py'`. FFmpeg and ffprobe are required to exercise the real audio/video recording test.
