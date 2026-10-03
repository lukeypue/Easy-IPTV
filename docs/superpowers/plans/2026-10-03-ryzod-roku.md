# RYZOD Roku Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement inline; one whole-branch review after integration.

**Goal:** Deliver a testable native Roku port based on Android 4.70 with the same RYZOD navigation and an optional LAN DVR/download companion.
**Architecture:** Pure BrightScript models, SceneGraph screens, asynchronous Task requests, one Video node, bounded registry persistence, optional authenticated Python/SQLite companion.
**Tech Stack:** BrightScript, SceneGraph XML, BrighterScript, brs headless interpreter, Python standard library.
**Spec:** docs/superpowers/specs/2026-10-03-ryzod-roku-design.md

## Global Constraints
- Base commit: 637c449f79db1f2d784d0561041a06be750c779b; branch feature/ryzod-roku.
- Only roku/, Roku workflow and these design/plan documents change; Android/Apple source and updater manifests remain untouched.
- Three playlists; 28 KB registry budget; seven days; 24 hours; five-minute manual time steps.
- Playback first, Close last; confirm stopping/deleting; preserve guide position and old data on failed refresh.
- No claimed hardware test or universal codec/pause/DVR parity.

## Review Focus
- Stale network responses after playlist changes must not leak content or favorites.
- Slow/unreachable provider must time out and allow Back/retry.
- Large catalogs must not create thousands of SceneGraph nodes or stall render thread.
- Companion interruption and Range rejection must preserve correct partial download bytes.
- Playback completion/error/preview/fullscreen transitions must retain navigation and use one stream.

### Task 1: Provider and UI domain core
**Files:** roku/source/core/*.brs, roku/tests/core.test.js, roku/package.json, roku/bsconfig.json.
**Interfaces:** API request URLs; normalized media item {id,kind,title,url,format,category,icon}; guide cells {start,end,title,gap}; immutable state helpers.
- [x] Write failing behavioral tests in the BrightScript harness; run and observe missing implementation.
- [x] Implement normalization/parsers, wrapped catalog arrays, encoded URLs, gaps/duration clipping, scoped keys, manual time validation, three-page keyboard and navigation.
- [x] Run full core suite and compiler; commit.

### Task 2: Native screens and provider tasks
**Files:** roku/components/*.xml, roku/components/*.brs, roku/source/main.brs, roku/manifest, roku/images/*.
**Interfaces:** RequestTask request/response {id,generation,kind,playlist,params}; Guide callbacks feed normalized cells; Registry state stores playlists/favorites/resume/settings.
- [x] Add package/SceneGraph contract tests before writing screens.
- [x] Implement one player, menu, login keyboard, catalogs, series episodes, favorites, search, guide, mini guide, playlists and settings.
- [x] Add async timeouts and response generation guards; failed refresh keeps cached guide.
- [x] Compile, validate callbacks/fields/package/assets, rerun core suite; commit.

### Task 3: Optional companion and job UI
**Files:** roku/companion/server.py, roku/companion/test_server.py, roku/components/* (job UI), roku/tests/core.test.js (job actions).
**Interfaces:** /api/status, /api/jobs, /api/jobs/{id}/{pause,resume,stop,delete}, /media/{id}; Authorization Bearer token; SQLite job records; schedule ISO/epoch, duration, source URL.
- [x] Write failing persistence/HTTP/worker tests with local fixture server.
- [x] Implement token-authenticated bounded API, durable schedules/retries, recording and sequential resumable downloads, Range streaming and stop/delete controls.
- [x] Integrate Roku jobs and manual schedule UI; require user confirmation before destructive action.
- [x] Run both suites and compile; commit.

### Task 4: Delivery and final review
**Files:** roku/tools/package.py, roku/README.md, roku/TEST_REPORT.md, .github/workflows/roku.yml.
- [x] Validate clean packaging, deterministic ZIP, no secrets/provider data, native field contracts and source assets.
- [x] Run suites and compile on final tree; inspect diff against baseline.
- [x] Perform whole-branch review; reproduce significant findings as failing tests, fix, rerun suites.
- [x] Push only feature/ryzod-roku; record commit and checksum; save standalone ZIP and companion deliverable; report hardware verification boundary.
