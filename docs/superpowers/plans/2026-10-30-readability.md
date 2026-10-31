# RYZOD 4.84 Readability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep search focus on the keyboard while typing, switch navigation/category bubbles to violet with white text, and enlarge app-wide text by 30%.

**Architecture:** Consume explicit requests to focus results once in the stable search parent; ordinary editing cancels pending requests. Apply the text multiplier through an activity configuration context so Compose, native controls and app dialogs share the same font scale. Accommodate larger text in fixed-height rows and narrow controls without changing browsing or playback behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Android/Media3, Robolectric and GitHub Actions.

**Spec:** User request and RELEASE_4.84.md.

## Global Constraints

- Base is full 4.83 source, not obsolete main application code.
- Version 4.84/code 108; permanent package and signing key unchanged.
- Preserve existing accessibility font scale, remembered results/guide and Smooth settings.
- Keep the keyboard about 60% of TV width and usable on phones.

## Review Focus

- Returning to the keyboard after Done or bottom-row Down must not replay a prior results-focus request.
- Explicit Done/Down must still work after changing the query, including delayed matches.
- Larger text must fit TV guide/mini-guide rows and phone keyboard action labels.
- Native player labels and app dialogs must use the same text increase without repeated scaling.
- Existing system font settings must remain effective.

## Tasks

- [x] Add SearchFocusUiTest covering submit, return to keyboard, type and wait for multiple settled queries; reproduce failure before changing focus behavior.
- [x] Replace persistent/replayed focus requests with a consumed parent-owned intent; cancel it when editing starts or changes the query. Verify explicit exits still focus results.
- [x] Add AppReadability context helper and tests; integrate MainActivity before UI creation and adapt fixed-height text containers.
- [x] Replace navigation/category fills with violet/white, retaining visible selection/focus. Capture TV/phone search, guide, menus and settings at the actual enlarged scale; inspect for clipping.
- [x] Run focused and complete regression suites, review independently, lint and signed-build both distributions; verify release and both updater manifests before completion.

## Accessibility follow-up (4.85 / code 109)

A final real Android activity-context test at system font scale 1.5 reproduced PREVIOUS being truncated. Android nonlinear font conversion means converting a combined 72sp width underestimates six actual 12sp label units. Convert the actual label size first, then estimate word width. This check failed on 4.84 and passed with the corrected sizing; retain it in AppReadabilityTest. Publish a new immutable 4.85 release with all prior readability changes and 245 full-suite tests per distribution.
