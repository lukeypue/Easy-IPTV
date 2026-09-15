# Zako Release & Direct Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a non-debug, permanently signable Zako APK and a stable direct-download landing page without changing playback, DVR, recording, or download behavior.

**Architecture:** Keep the existing debug CI for development. Add a separate release workflow that can compile unsigned release candidates before secrets are configured and automatically signs them once the permanent Zako keystore is stored in GitHub Actions secrets. Publish a lightweight GitHub Pages landing page whose download button targets the stable `releases/latest/download/Zako.apk` URL.

**Tech Stack:** Android Gradle Kotlin DSL, GitHub Actions, APK signing, GitHub Pages.

**Spec:** User-approved release hardening and direct Fire TV distribution workflow discussed in project conversation.

## Global Constraints

- Package name remains `com.easyiptv.player`.
- Version remains `4.24` / versionCode `49` for this test release.
- No playback, DVR, recording, downloader, or UI behavior changes in this release-hardening pass.
- The permanent private signing key must never be committed to the repository.
- Release APK must not be debuggable.
- Release merged manifest must not contain `android.permission.DUMP`.
- Future direct APK updates must use the same signing certificate.

---

### Task 1: Release signing configuration

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: `ZAKO_KEYSTORE_PATH`, `ZAKO_STORE_PASSWORD`, `ZAKO_KEY_ALIAS`, `ZAKO_KEY_PASSWORD`.
- Produces: signed release APK when all four values are present; unsigned release APK otherwise.

- [ ] Add conditional release signing configuration driven only by environment variables.
- [ ] Explicitly set `release.isDebuggable = false`.
- [ ] Build via GitHub Actions to verify Gradle configuration.

### Task 2: Protected release workflow

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: four GitHub Actions secrets matching Task 1.
- Produces: `Zako-v4.24-release` artifact.

- [ ] Compile `assembleRelease`.
- [ ] Verify the merged release manifest is not debuggable.
- [ ] Verify `android.permission.DUMP` is absent.
- [ ] When signing secrets exist, verify the APK certificate SHA-256 equals the permanent Zako certificate.
- [ ] Upload the resulting APK artifact.

### Task 3: Stable download landing page

**Files:**
- Create: `docs/index.html`

**Interfaces:**
- Consumes: GitHub Release asset named `Zako.apk`.
- Produces: stable landing page suitable for a Downloader short code.

- [ ] Add Zako blue/teal branding.
- [ ] Link the primary button to `/releases/latest/download/Zako.apk`.
- [ ] Add Fire TV installation steps.
- [ ] Add bring-your-own-content disclaimer.

### Task 4: Verification

- [ ] Confirm GitHub release workflow succeeds as an unsigned release candidate before secrets exist.
- [ ] Download the unsigned release artifact.
- [ ] Sign it with the permanent private Zako key outside the public repository.
- [ ] Verify certificate fingerprint and `debuggable=false`.
- [ ] Save a private keystore backup for the owner.
