# RYZOD 4.73 — Movie Poster Recovery

- Prevent duplicate movie rows with blank artwork from erasing a valid poster.
- Keep saved artwork when a successful refresh omits the address for the same movie.
- Use the provider’s movie-details artwork when a visible movie card lacks a poster or its primary picture fails. Works in Movies and Search.
- Remember successfully loaded fallback pictures across scrolling and restarts; a failed primary image cannot delete a working fallback.
- Keep the lazy grid and existing bitmap memory limit. Artwork lookups are limited to two requests, coalesced per movie, cancellable on disposal, and bounded in memory. One image retry avoids permanent blank cards after a transient failure.
- Preserve Series and all 4.72 guide, keyboard, recording and playback improvements.

Validation: catalog and cancellation regressions; real bitmap load after lazy-grid scroll away/back; cached fallback with provider unavailable; bounded lookup and retry behavior; full automated suite and Android lint; signed release and both updater manifests verified by the release pipeline.

Physical Fire Stick/provider validation remains necessary. No provider credentials or original catalog response were available; the tests reproduce the app’s data-loss and lifecycle cases with controlled inputs.
