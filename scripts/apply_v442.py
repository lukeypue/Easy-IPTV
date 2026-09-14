from pathlib import Path

p = Path('app/src/main/java/com/easyiptv/player/MainActivity.kt')
s = p.read_text()

def rep(old: str, new: str, label: str):
    global s
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, found {n}')
    s = s.replace(old, new, 1)

# Imports used by the SBS 3D -> 2D crop.
rep(
    'import androidx.compose.ui.draw.clip\n',
    'import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.clipToBounds\n',
    'clipToBounds import'
)
rep(
    'import androidx.compose.ui.graphics.StrokeCap\n',
    'import androidx.compose.ui.graphics.StrokeCap\nimport androidx.compose.ui.graphics.TransformOrigin\n',
    'TransformOrigin import'
)

# Hard input gate while the fresh live lineup is settling.
rep(
'''    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            PlayerKeys.priority?.invoke(event.keyCode) == true
        ) return true
        return super.dispatchKeyEvent(event)
    }
''',
'''    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // A cached playlist may be visible before the fresh provider refresh is
        // finished. During that short window, swallow remote input so nobody can
        // drive into half-built lists and trigger the startup crash/glitch path.
        if (AppInputGate.startupLocked) return true
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            PlayerKeys.priority?.invoke(event.keyCode) == true
        ) return true
        return super.dispatchKeyEvent(event)
    }
''',
    'activity input gate'
)

rep(
'''    var reload by remember { mutableIntStateOf(0) }

    // Remembered across screens so "back" lands where you left off.
''',
'''    var reload by remember { mutableIntStateOf(0) }
    // Cached data may paint immediately, but interaction stays locked until the
    // fresh live refresh has completed (successfully or with a safe cached fallback).
    var startupSettled by remember(activeIdx, reload) { mutableStateOf(false) }

    // Remembered across screens so "back" lands where you left off.
''',
    'startup settled state'
)

# Do not allow a USB prompt to steal focus while startup itself is locked.
rep('    if (showDrivePrompt) {\n', '    if (showDrivePrompt && startupSettled) {\n', 'drive prompt startup gate')

# Global gate follows Compose readiness and is always released if this App leaves composition.
rep(
'''    val cacheKey = remember(playlists, activeIdx) {
        playlists.getOrNull(activeIdx)?.let { DataCache.keyFor(it) }
    }

    LaunchedEffect(source, reload) {
''',
'''    val cacheKey = remember(playlists, activeIdx) {
        playlists.getOrNull(activeIdx)?.let { DataCache.keyFor(it) }
    }

    LaunchedEffect(playlists.isEmpty(), startupSettled) {
        AppInputGate.startupLocked = StartupPolicy.shouldBlockInput(
            hasPlaylist = playlists.isNotEmpty(),
            refreshSettled = startupSettled
        )
    }
    DisposableEffect(Unit) {
        onDispose { AppInputGate.startupLocked = false }
    }

    LaunchedEffect(source, reload) {
        startupSettled = source == null
''',
    'startup gate effect'
)

rep(
'''            } catch (e: Exception) {
                if (data == null) loadError = e.message ?: "error"
            }
        }
    }

    // XMLTV can be huge.
''',
'''            } catch (e: Exception) {
                if (data == null) loadError = e.message ?: "error"
            } finally {
                // Unlock only after the fresh provider attempt has finished. If
                // it failed but a cache exists, the cache is now a deliberate
                // fallback rather than an accidental half-loaded startup state.
                startupSettled = true
            }
        }
    }

    // XMLTV can be huge.
''',
    'startup settle finally'
)

# Autotune must not race the fresh lineup refresh.
rep(
'''    LaunchedEffect(data) {
        if (autoTuned || data == null || nav !is Nav.Home) return@LaunchedEffect
''',
'''    LaunchedEffect(data, startupSettled) {
        if (!startupSettled || autoTuned || data == null || nav !is Nav.Home) return@LaunchedEffect
''',
    'autotune startup gate'
)

# Full-screen startup interstitial even when a cache was available instantly.
rep(
'''    when {
        playlists.isEmpty() -> AddPlaylistScreen(first = true, onSaved = { addPlaylist(it) }, onBack = null)
        nav is Nav.AddPlaylist -> AddPlaylistScreen(first = false, onSaved = { addPlaylist(it) }, onBack = { nav = Nav.Home })
''',
'''    when {
        playlists.isEmpty() -> AddPlaylistScreen(first = true, onSaved = { addPlaylist(it) }, onBack = null)
        !startupSettled -> StartupLoadingScreen(playlists.getOrNull(activeIdx)?.name.orEmpty())
        nav is Nav.AddPlaylist -> AddPlaylistScreen(first = false, onSaved = { addPlaylist(it) }, onBack = { nav = Nav.Home })
''',
    'startup loading route'
)

# Loading screen copy: intentional wait, no half-loaded navigation.
rep(
'''@Composable
private fun LoadingBox(msg: String) {
''',
'''@Composable
private fun StartupLoadingScreen(playlistName: String) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ZAKO", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(18.dp))
        CircularProgressIndicator(color = Accent)
        Spacer(Modifier.height(18.dp))
        Text("Preparing your TV experience…", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(
            "Please wait while Zako refreshes your channels and gets everything ready for smooth browsing and playback.",
            color = Muted, fontSize = 13.sp
        )
        if (playlistName.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(playlistName, color = Accent, fontSize = 11.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text("Usually only takes a few seconds.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun LoadingBox(msg: String) {
''',
    'startup loading composable'
)

# Search Live TV becomes a dense horizontal channel strip instead of movie-shaped rows.
rep(
'''            if (liveHits.isNotEmpty()) {
                item { SectionHeader("Live TV") }
                items(liveHits) { ch ->
                    MediaRow(ch.name, ch.icon, onClick = { saveRecent(q); playLiveHit(ch) })
                }
            }
''',
'''            if (liveHits.isNotEmpty()) {
                item { SectionHeader("Live TV") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(liveHits, key = { it.id }) { ch ->
                            SearchLiveCard(ch) { saveRecent(q); playLiveHit(ch) }
                        }
                    }
                }
            }
''',
    'search live layout'
)

rep(
'''@Composable
private fun SectionHeader(label: String) {
''',
'''@Composable
private fun SearchLiveCard(ch: LiveChannel, onClick: () -> Unit) {
    val guideReady = EpgStore.loaded.value
    val nowMs = System.currentTimeMillis()
    val nowTitle = if (guideReady) {
        EpgStore.guide(ch.epgId, ch.name)
            .firstOrNull { nowMs in it.startMs until it.endMs }
            ?.title
    } else null

    Column(
        Modifier
            .width(148.dp)
            .tvFocus(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            if (!ch.icon.isNullOrBlank()) {
                AsyncImage(
                    model = ch.icon,
                    contentDescription = ch.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            } else {
                // Never turn a channel number into a giant fake poster ("2", "7", etc.).
                Text("LIVE", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            ch.name, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (!nowTitle.isNullOrBlank()) {
            Text(
                nowTitle, color = Accent, fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
''',
    'search live card'
)

# Per-channel manual SBS 3D -> normal 2D view. Automatic detection is intentionally avoided.
rep(
'''    val currentIdx by Playback.currentIdxC
    val current = queue[currentIdx.coerceIn(0, queue.size - 1)]
    var nowNext by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
''',
'''    val currentIdx by Playback.currentIdxC
    val current = queue[currentIdx.coerceIn(0, queue.size - 1)]
    val sbsPrefKey = remember(current.url) { "sbs_2d_${current.url.hashCode()}" }
    var sbs2d by remember(current.url) { mutableStateOf(prefs.getBoolean(sbsPrefKey, false)) }
    var nowNext by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
''',
    'sbs state'
)

rep(
'''    fun cyclePictureSize() {
''',
'''    fun toggleSbs2d() {
        sbs2d = !sbs2d
        prefs.edit().putBoolean(sbsPrefKey, sbs2d).apply()
        toast(
            context,
            if (sbs2d) "3D side-by-side correction on for this channel."
            else "3D side-by-side correction off."
        )
    }

    fun cyclePictureSize() {
''',
    'sbs toggle function'
)

# Clip the doubled left-eye image to the TV bounds.
rep(
'''            .fillMaxSize()
            .background(Color.Black)
            // Phones & tablets: swipe up = previous channel in the list,
''',
'''            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            // Phones & tablets: swipe up = previous channel in the list,
''',
    'player clip bounds'
)

rep(
'''                .graphicsLayer(
                    // FULL SCREEN mode: blow the picture up 34% past the edges —
                    // wipes out black bars even when they're part of the channel's
                    // own picture. Old-school edge-to-edge TV.
                    scaleX = if (superStretch) 1.34f else 1f,
                    scaleY = if (superStretch) 1.34f else 1f
                )
''',
'''                .graphicsLayer(
                    // SBS 3D providers send left/right eye images squeezed into
                    // one frame. Double from the LEFT edge to show the left eye as
                    // ordinary 2D. This is manual/per-channel because metadata
                    // cannot reliably distinguish SBS from a normal split-screen.
                    scaleX = when {
                        sbs2d -> 2f
                        superStretch -> 1.34f
                        else -> 1f
                    },
                    scaleY = if (superStretch && !sbs2d) 1.34f else 1f,
                    transformOrigin = if (sbs2d) TransformOrigin(0f, 0.5f) else TransformOrigin.Center
                )
''',
    'sbs graphics transform'
)

rep(
'''                    prefs = prefs,
                    ccEnabled = ccEnabled,
''',
'''                    prefs = prefs,
                    ccEnabled = ccEnabled,
                    sbs2d = sbs2d,
''',
    'mini guide sbs arg'
)
rep(
'''                    onToggleCc = { setCcEnabled(!ccEnabled) },
                    afrEnabled = matchFps,
''',
'''                    onToggleCc = { setCcEnabled(!ccEnabled) },
                    onToggleSbs = { toggleSbs2d() },
                    afrEnabled = matchFps,
''',
    'mini guide sbs callback'
)

rep(
'''    prefs: SharedPreferences,
    ccEnabled: Boolean,
    onToggleCc: () -> Unit,
    afrEnabled: Boolean,
''',
'''    prefs: SharedPreferences,
    ccEnabled: Boolean,
    sbs2d: Boolean,
    onToggleCc: () -> Unit,
    onToggleSbs: () -> Unit,
    afrEnabled: Boolean,
''',
    'mini guide signature sbs'
)

rep(
'''    val modeFocus = remember { FocusRequester() }
    val sizeFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
''',
'''    val modeFocus = remember { FocusRequester() }
    val sizeFocus = remember { FocusRequester() }
    val sbsFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
''',
    'sbs focus requester'
)

rep(
'''            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.8f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = previousFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = sizeFocus; right = settingsFocus; down = timelineFocus }
''',
'''            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.8f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = sbsFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                if (sbs2d) "3D→2D" else "3D",
                modifier = Modifier.weight(0.78f).focusRequester(sbsFocus).focusProperties {
                    left = sizeFocus; right = previousFocus; down = timelineFocus
                },
                activeColor = if (sbs2d) Accent else Ink
            ) { touch(); onToggleSbs() }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = sbsFocus; right = settingsFocus; down = timelineFocus }
''',
    'sbs mini guide control'
)

# Storage labels that also make sense on an Android phone/tablet.
s = s.replace('"Fire TV internal: ${if (internalFree >= 0)', '"Device storage: ${if (internalFree >= 0)')
s = s.replace('Chip("Fire Stick", !extOn)', 'Chip("Device", !extOn)')
s = s.replace('(if (onDrive) "External drive: " else "Fire Stick storage: ")', '(if (onDrive) "External drive: " else "Device storage: ")')

p.write_text(s)
print('v4.42 MainActivity transformation applied successfully')
