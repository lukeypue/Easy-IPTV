package com.easyiptv.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ----------------------------- palette ----------------------------- */
private val Bg = Color(0xFF0E0F13)
private val SurfaceCol = Color(0xFF171922)
private val Surface2 = Color(0xFF1F2230)
private val Line = Color(0xFF2A2E3D)
private val Ink = Color(0xFFF2F3F5)
private val Muted = Color(0xFF8A8F9A)
private val Accent = Color(0xFFF5B944)
private val Live = Color(0xFFFF3B5C)

private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF20160A),
    background = Bg,
    onBackground = Ink,
    surface = SurfaceCol,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Live,
    onError = Color.White
)

/* Fire TV remote: draw a gold outline around whatever the D-pad has focused. */
/** The remote's highlighter: hot pink, thick, with a soft glow behind it —
 *  you can always tell exactly where you are, even against busy backgrounds. */
private val FocusPink = Color(0xFFFF2FB9)

/** Walks the player's own control panel (play/pause, FF, RW, sound, settings,
 *  progress bar) and gives every button the same hot-pink focus glow as the
 *  rest of the app, plus paints the "buffered ahead" part of the progress bar
 *  in bright cyan so you can SEE how much smooth video is stored up. */
@OptIn(UnstableApi::class)
private fun tintPlayerControls(root: android.view.View) {
    val pink = 0xFFFF2FB9.toInt()
    fun walk(v: android.view.View) {
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        when (v) {
            is androidx.media3.ui.DefaultTimeBar -> {
                v.setPlayedColor(0xFFF5B944.toInt())      // watched: gold
                v.setBufferedColor(0xFF33E1FF.toInt())    // stored ahead: bright cyan
                v.setUnplayedColor(0x55FFFFFF)            // rest: faint white
                v.setScrubberColor(pink)                  // the grab handle: pink
            }
            is android.widget.ImageButton, is android.widget.Button -> {
                val glow = android.graphics.drawable.StateListDrawable().apply {
                    addState(
                        intArrayOf(android.R.attr.state_focused),
                        android.graphics.drawable.GradientDrawable().apply {
                            setColor(0x40FF2FB9)
                            cornerRadius = 28f
                            setStroke(5, pink)
                        }
                    )
                    addState(intArrayOf(), android.graphics.drawable.ColorDrawable(0x00000000))
                }
                v.background = glow
            }
        }
    }
    walk(root)
}

private fun Modifier.tvFocus(shape: RoundedCornerShape = RoundedCornerShape(14.dp)): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        this
            .onFocusChanged { focused = it.isFocused }
            .background(
                color = if (focused) FocusPink.copy(alpha = 0.18f) else Color.Transparent,
                shape = shape
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) FocusPink else Color.Transparent,
                shape = shape
            )
    }

/** One inexpensive CC switch for embedded/subtitle tracks Media3 already exposes. */
private fun applyCaptionPreference(player: Player?, enabled: Boolean) {
    player ?: return
    runCatching {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .setSelectTextByDefault(enabled)
            .setSelectUndeterminedTextLanguage(enabled)
            .build()
    }
}

/* ----------------------------- navigation ----------------------------- */

data class Playable(
    val name: String,
    val url: String,
    val isLive: Boolean,
    val epgId: String? = null,
    val guideKey: String? = null,
    val canRecord: Boolean = false,
    val artwork: String? = null
)

sealed class Nav {
    object Home : Nav()
    data class Play(
        val queue: List<Playable>,
        val start: Int = 0,
        val from: Nav = Home,
        val startAtMs: Long? = null,   // jump straight to this spot
        val attach: Boolean = false    // corner → full screen: SAME stream, no reload
    ) : Nav()
    data class Series(val s: SeriesItem) : Nav()
    object AddPlaylist : Nav()
}

/** What keeps playing in the corner after you back out of full screen. */
data class MiniState(val queue: List<Playable>, val index: Int, val posMs: Long)

/* ----------------------------- activity ----------------------------- */

/* Safety net for TV remotes: any button press no screen element handled lands here,
 * so the player can always react — menus can never become unreachable. */
object PlayerKeys {
    var handler: ((Int) -> Boolean)? = null

    /** Checked BEFORE the on-screen views get the press — used for channel
     *  up/down zapping, which must win over the video view's own key handling. */
    var priority: ((Int) -> Boolean)? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    App()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            PlayerKeys.priority?.invoke(event.keyCode) == true
        ) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (PlayerKeys.handler?.invoke(keyCode) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        Playback.resumeFromBackground()
    }

    override fun onStop() {
        // Fire TV storage + provider safety: when EZTV is hidden, stop the live
        // provider/DVR path instead of quietly writing video in the background.
        // The only exception is an active recording, which owns the one stream.
        if (isChangingConfigurations) Playback.player?.pause()
        else Playback.suspendForBackground()
        super.onStop()
    }

    override fun onDestroy() {
        Playback.releaseAll()
        super.onDestroy()
    }
}

/* ----------------------------- root ----------------------------- */
@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE) }

    var playlists by remember { mutableStateOf(PlaylistStore.load(prefs)) }
    var activeIdx by remember { mutableIntStateOf(PlaylistStore.activeIndex(prefs)) }
    var nav by remember { mutableStateOf<Nav>(Nav.Home) }
    var data by remember { mutableStateOf<AppData?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    // Remembered across screens so "back" lands where you left off.
    var railSection by remember { mutableStateOf("live") }
    var railDepth by remember { mutableIntStateOf(1) }   // 0 = main menu, 1 = inside a section
    var liveCat by remember(activeIdx) { mutableStateOf("all") }
    var movieCat by remember(activeIdx) { mutableStateOf("all") }
    var seriesCat by remember(activeIdx) { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    // Heavy VOD/series JSON never competes with live playback at startup.
    // Fetch it once per session only when the viewer actually enters Movies,
    // Series, or Search. Cached catalog data can still appear immediately.
    var catalogLoadedThisSession by remember(activeIdx, reload) { mutableStateOf(false) }
    var catalogLoading by remember(activeIdx, reload) { mutableStateOf(false) }
    var catalogError by remember(activeIdx, reload) { mutableStateOf<String?>(null) }
    var catalogRetry by remember(activeIdx, reload) { mutableIntStateOf(0) }

    // One-time "external drive found — use it?" prompt. Shows only if a drive is
    // plugged in, the setting is off, and we haven't asked about THIS drive yet.
    var showDrivePrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)   // let the app settle first
        val present = Storage.drivePresent(context)
        val asked = prefs.getBoolean("ext_prompt_shown", false)
        if (present && !Storage.isEnabled(prefs) && !asked) showDrivePrompt = true
    }
    if (showDrivePrompt) {
        val driveGb = remember { Storage.driveFreeBytes(context) }
        AlertDialog(
            onDismissRequest = {
                showDrivePrompt = false
                prefs.edit().putBoolean("ext_prompt_shown", true).apply()
            },
            containerColor = SurfaceCol,
            title = { Text("External drive found", color = Ink) },
            text = {
                Text(
                    "Save downloads and recordings to your plugged-in drive" +
                        (if (driveGb >= 0) " (${Storage.gb(driveGb)} GB free)" else "") +
                        "? This keeps your Fire Stick's storage from filling up. You can change this anytime in Settings.",
                    color = Muted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Storage.setEnabled(prefs, true)
                    prefs.edit().putBoolean("ext_prompt_shown", true).apply()
                    showDrivePrompt = false
                    toast(context, "External storage enabled for new downloads and recordings.")
                }) { Text("Use the drive", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("ext_prompt_shown", true).apply()
                    showDrivePrompt = false
                }) { Text("Keep on Fire Stick", color = Muted) }
            }
        )
    }

    // The corner mini player: whatever you backed out of keeps playing here.
    var mini by remember { mutableStateOf<MiniState?>(null) }
    // Only auto-tune to the last channel once per app start.
    var autoTuned by remember { mutableStateOf(false) }

    fun openPlay(p: Nav.Play) {
        val target = p.queue.getOrNull(p.start)
        val remoteTarget = target?.url?.let { !it.startsWith("/") && !it.startsWith("file:") } == true
        if (remoteTarget) {
            val maxStreams = ProviderStreams.max(prefs)
            val downloadSlots = ProviderStreams.downloadSlots(context, prefs)
            val recordingActive = Recorder.activeName.value != null
            val cur = Playback.queue.getOrNull(Playback.currentIdxC.intValue)
            val sameLive = target?.isLive == true && cur?.isLive == true && target.url == cur.url

            // Starting a new player item replaces the current playback slot, so
            // only background download/direct-recording connections are additive.
            var backgroundSlots = downloadSlots + ProviderStreams.recordingSlots()
            // A same-channel tee recording currently costs 0, but if the viewer
            // changes away from that channel it must become a direct recording.
            if (recordingActive && !sameLive && ProviderStreams.recordingSlots() == 0) {
                backgroundSlots += 1
            }
            if (1 + backgroundSlots > maxStreams) {
                val msg = when {
                    recordingActive && maxStreams == 1 ->
                        "Recording is using your 1-stream IPTV plan. Stay on this channel, stop recording, or set Provider streams to 2/3 only if your service includes them."
                    recordingActive ->
                        "No provider stream is free for that change. Your EZTV limit is $maxStreams; stop a recording/download or raise it only if your IPTV plan allows more."
                    downloadSlots > 0 ->
                        "A download is using your available IPTV stream. Stop it in Downloads or raise Settings → Provider streams if your plan includes more connections."
                    else -> "No provider stream is free. Check Settings → Provider streams."
                }
                toast(context, msg)
                return
            }
        }
        mini = null
        if (target?.isLive == true) {
            prefs.edit()
                .putString("last_live_name", target.name)
                .putString("last_live_url", target.url)
                .putString("last_live_epg", target.epgId ?: "")
                .putString("last_live_guide", target.guideKey ?: "")
                .apply()
        }
        nav = p
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DownloadStore.migrateLegacyRetention(prefs)
            DownloadStore.cleanup(context, prefs)
            ScheduleStore.cleanup(prefs)
        }
    }

    // Recording shows a notification; Android 13+ wants permission for that.
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (activeIdx >= playlists.size) activeIdx = 0
    val source = remember(playlists, activeIdx) {
        playlists.getOrNull(activeIdx)?.let { buildSource(it) }
    }

    val cacheKey = remember(playlists, activeIdx) {
        playlists.getOrNull(activeIdx)?.let { DataCache.keyFor(it) }
    }

    LaunchedEffect(source, reload) {
        data = null
        loadError = null
        EpgStore.clear()
        if (source != null) {
            var cached: AppData? = null
            // 1) Open INSTANTLY with the saved copy from last time (if we have one).
            if (cacheKey != null) {
                cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DataCache.load(context, cacheKey)
                }
                if (cached != null) data = cached
            }
            // 2) Refresh LIVE first. Xtream can do this with only two small calls,
            // so live playback is not fighting VOD/series JSON parsing at startup.
            try {
                val liveFresh = source.loadLiveOnly()
                val merged = if (source.supportsSeries) {
                    AppData(
                        liveCats = liveFresh.liveCats, live = liveFresh.live,
                        vodCats = cached?.vodCats ?: liveFresh.vodCats,
                        movies = cached?.movies ?: liveFresh.movies,
                        seriesCats = cached?.seriesCats ?: liveFresh.seriesCats,
                        series = cached?.series ?: liveFresh.series
                    )
                } else {
                    // M3U has no separate VOD/series API; loadLiveOnly() is already
                    // the complete refresh, so do not fetch the same playlist twice.
                    liveFresh
                }
                data = merged

                // Save the refreshed live lineup plus any cached catalog. Do NOT
                // start a giant VOD/series refresh on a timer behind live TV.
                // The on-demand effect below loads it only when the user asks.
                if (cacheKey != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DataCache.save(context, cacheKey, merged)
                }
            } catch (e: Exception) {
                if (data == null) loadError = e.message ?: "error"
            }
        }
    }

    // XMLTV can be huge. Never download/parse the full guide behind full-screen
    // playback. PlayerScreen uses the provider's tiny now/next request instead.
    // Load XMLTV only when the viewer is actually browsing Live/Search, and
    // Simple Mode skips it entirely to protect troublesome channels.
    LaunchedEffect(data, source, nav, railSection) {
        val s = source ?: return@LaunchedEffect
        if (data == null || nav is Nav.Play || prefs.getBoolean("simple_mode", true)) return@LaunchedEffect
        // Do not start XMLTV from Search: Search may already be lazily loading
        // the large Movies/Series catalog. Two large parses at once is exactly
        // the kind of CPU/GC competition we are removing for Fire TV.
        if (railSection != "live") return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        EpgStore.load(s.xmltvUrl())
    }

    // True lazy loading: Xtream movie/series catalogs are often huge. They load
    // only when the viewer opens an on-demand/search screen, never on a timer
    // behind live TV. This applies in normal AND Simple Mode.
    LaunchedEffect(railSection, source, activeIdx, data, catalogLoadedThisSession, catalogRetry) {
        val s = source ?: return@LaunchedEffect
        val needsCatalog = railSection == "movies" || railSection == "series" || railSection == "search"
        val liveBase = data ?: return@LaunchedEffect
        if (!needsCatalog || !s.supportsSeries || catalogLoadedThisSession) return@LaunchedEffect
        catalogLoading = true
        catalogError = null
        try {
            val catalog = s.loadOnDemandOnly()
            val merged = AppData(
                liveCats = liveBase.liveCats, live = liveBase.live,
                vodCats = catalog.vodCats, movies = catalog.movies,
                seriesCats = catalog.seriesCats, series = catalog.series
            )
            data = merged
            catalogLoadedThisSession = true
            if (catalog.movies.isEmpty() && catalog.series.isEmpty()) {
                catalogError = "Your provider returned no Movies or Series on this request. Press Retry; if it repeats, the next test should capture the provider response/error."
            }
            if (cacheKey != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                DataCache.save(context, cacheKey, merged)
            }
        } catch (e: Exception) {
            // Stop automatic retry loops on a weak Fire Stick. The viewer gets
            // one explicit Retry button instead, with the actual failure text.
            catalogError = e.message ?: "On-demand catalog request failed"
            catalogLoadedThisSession = true
        } finally {
            catalogLoading = false
        }
    }

    // Restore the recent-channel surf strip across app restarts. This is done
    // only when playlist data changes — zero periodic work while watching.
    LaunchedEffect(data, activeIdx) {
        data?.live?.let { channels ->
            RecentChannels.restore(prefs, channels.map { livePlayable(prefs, it) })
        }
    }

    // Cable-box behavior: the app opens straight onto the channel you were
    // last watching (Settings › "Start on last channel" turns this off).
    LaunchedEffect(data) {
        if (autoTuned || data == null || nav !is Nav.Home) return@LaunchedEffect
        autoTuned = true
        if (!prefs.getBoolean("autoplay_last", true)) return@LaunchedEffect
        val url = prefs.getString("last_live_url", null) ?: return@LaunchedEffect
        // Build the full channel lineup and start on the saved channel — so
        // channel up/down works the moment the app opens, like a cable box.
        val channels = data?.live ?: return@LaunchedEffect
        val idx = channels.indexOfFirst { it.url == url || it.url == tsUrl(url) || liveAutoUrl(prefs, it.url) == url }
        if (idx < 0) return@LaunchedEffect   // channel no longer in this playlist
        openPlay(
            Nav.Play(
                channels.map { livePlayable(prefs, it) },
                idx,
                from = Nav.Home
            )
        )
    }

    fun addPlaylist(p: Playlist) {
        val next = playlists + p
        playlists = next
        PlaylistStore.save(prefs, next)
        activeIdx = next.size - 1
        PlaylistStore.setActive(prefs, activeIdx)
        nav = Nav.Home
    }

    when {
        playlists.isEmpty() -> AddPlaylistScreen(first = true, onSaved = { addPlaylist(it) }, onBack = null)
        nav is Nav.AddPlaylist -> AddPlaylistScreen(first = false, onSaved = { addPlaylist(it) }, onBack = { nav = Nav.Home })
        nav is Nav.Play -> {
            val pl = nav as Nav.Play
            PlayerScreen(
                queue = pl.queue,
                start = pl.start,
                startAtMs = pl.startAtMs,
                attach = pl.attach,
                source = source,
                prefs = prefs,
                onOpenSettings = { idx, posMs ->
                    // Keep the SAME stream in the corner, then open Settings.
                    // The mini-guide gear should never masquerade as a Back button.
                    mini = MiniState(pl.queue, idx, posMs)
                    railSection = "settings"
                    railDepth = 0
                    nav = Nav.Home
                },
                onBack = { idx, posMs ->
                    // Back doesn't stop or reconnect anything — the SAME stream
                    // just moves to the corner while you browse.
                    mini = MiniState(pl.queue, idx, posMs)
                    nav = pl.from
                }
            )
        }
        nav is Nav.Series && source != null -> {
            val cur = nav as Nav.Series
            SeriesDetailScreen(
                source = source,
                s = cur.s,
                prefs = prefs,
                onPlayQueue = { q, i -> openPlay(Nav.Play(q, i, from = cur)) },
                onBack = { nav = Nav.Home }
            )
        }
        else -> HomeScreen(
            prefs = prefs,
            playlistName = playlists.getOrNull(activeIdx)?.name ?: "",
            source = source,
            data = data,
            loadError = loadError,
            catalogLoading = catalogLoading,
            catalogError = catalogError,
            onRetryCatalog = {
                catalogError = null
                catalogLoadedThisSession = false
                catalogRetry++
            },
            activeIdx = activeIdx,
            section = railSection,
            depth = railDepth,
            mini = mini,
            onResumeMini = {
                val m = mini ?: return@HomeScreen
                // SAME stream — attach only, nothing reloads or reconnects.
                openPlay(Nav.Play(m.queue, m.index, from = Nav.Home, attach = true))
            },
            onCloseMini = {
                mini = null
                Playback.releaseAll()   // truly stop: close the one stream
            },
            onRoot = { id ->
                railSection = id
                railDepth = if (id == "live" || id == "movies" || id == "series") 1 else 0
                // Entering Search starts with an empty box (recent-search list
                // below stays) — no need to clear last time's text by hand.
                if (id == "search") searchQuery = ""
            },
            onBackToRoot = { railDepth = 0 },
            liveCat = liveCat, onLiveCat = { liveCat = it },
            movieCat = movieCat, onMovieCat = { movieCat = it },
            seriesCat = seriesCat, onSeriesCat = { seriesCat = it },
            searchQuery = searchQuery, onSearchQuery = { searchQuery = it },
            playlists = playlists,
            onSelectPlaylist = { i ->
                activeIdx = i
                PlaylistStore.setActive(prefs, i)
                reload++
            },
            onDeletePlaylist = { i ->
                val next = playlists.toMutableList().apply { removeAt(i) }
                playlists = next
                PlaylistStore.save(prefs, next)
                if (activeIdx >= next.size) {
                    activeIdx = 0
                    PlaylistStore.setActive(prefs, 0)
                }
                reload++
            },
            onAddPlaylist = { nav = Nav.AddPlaylist },
            onRetry = { reload++ },
            onPlay = { openPlay(Nav.Play(listOf(it), from = Nav.Home)) },
            onPlayLive = { q, i -> openPlay(Nav.Play(q, i, from = Nav.Home)) },
            onSeries = { nav = Nav.Series(it) }
        )
    }
}

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

/* ----------------------------- add playlist ----------------------------- */
@Composable
fun AddPlaylistScreen(first: Boolean, onSaved: (Playlist) -> Unit, onBack: (() -> Unit)?) {
    var type by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (onBack != null) BackHandler { if (type != null) type = null else onBack() }

    fun connect() {
        val p: Playlist = if (type == "m3u") {
            if (m3u.isBlank()) {
                status = "Paste your playlist link first."; statusIsError = true; return
            }
            Playlist(name = name.ifBlank { "My playlist" }, type = "m3u", url = m3u.trim())
        } else {
            if (host.isBlank() || user.isBlank() || pass.isBlank()) {
                status = "Fill in all three fields."; statusIsError = true; return
            }
            Playlist(
                name = name.ifBlank { "My playlist" }, type = "xtream",
                host = XtreamSource.normalizeHost(host), user = user.trim(), pass = pass.trim()
            )
        }
        loading = true; status = "Connecting…"; statusIsError = false
        scope.launch {
            val err = buildSource(p).test()
            if (err == null) {
                onSaved(p)
            } else {
                status = err; statusIsError = true; loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(Surface2, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(14.dp).background(Accent, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("EZTV", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Ink)
                Text("TV made simple", fontSize = 12.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(28.dp))

        if (type == null) {
            Text(
                if (first) "Let's set up your first playlist" else "Add a playlist",
                fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "How did your TV provider give you your login? Pick the one that matches.",
                fontSize = 14.sp, color = Muted
            )
            Spacer(Modifier.height(20.dp))
            BigOption(
                title = "Username & password",
                subtitle = "You have a server address, a username, and a password. (Most common)",
                onClick = { type = "xtream" }
            )
            Spacer(Modifier.height(12.dp))
            BigOption(
                title = "Playlist link (M3U)",
                subtitle = "You have one long web link, usually ending in .m3u or with \"get.php\" in it.",
                onClick = { type = "m3u" }
            )
            if (onBack != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "← Go back",
                    color = Muted, fontSize = 14.sp,
                    modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)).clickable { onBack() }.padding(8.dp)
                )
            }
        } else {
            Text(
                if (type == "m3u") "Paste your playlist link" else "Sign in to your service",
                fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, color = Ink
            )
            Spacer(Modifier.height(18.dp))
            TvTextField(
                value = name, onValueChange = { name = it },
                label = "Give it a name (optional)",
                placeholder = "e.g. Home, Sports, Backup",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (type == "m3u") {
                TvTextField(
                    value = m3u, onValueChange = { m3u = it },
                    label = "Playlist link",
                    placeholder = "http://…",
                    keyboardType = KeyboardType.Uri,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                TvTextField(
                    value = host, onValueChange = { host = it },
                    label = "Server address",
                    placeholder = "http://yourserver.com:8080",
                    keyboardType = KeyboardType.Uri,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                TvTextField(
                    value = user, onValueChange = { user = it },
                    label = "Username",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                TvTextField(
                    value = pass, onValueChange = { pass = it },
                    label = "Password",
                    password = true,
                    keyboardType = KeyboardType.Password,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Your login is saved on this device so you only enter it once.",
                fontSize = 12.sp, color = Muted
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { connect() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp).tvFocus(RoundedCornerShape(26.dp))
            ) {
                Text(if (loading) "Connecting…" else "Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "← Different login type",
                color = Muted, fontSize = 14.sp,
                modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)).clickable { type = null }.padding(8.dp)
            )
            if (status.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(status, fontSize = 13.sp, color = if (statusIsError) Live else Muted)
            }
        }
    }
}

@Composable
private fun BigOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocus()
            .background(SurfaceCol, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = Muted)
    }
}

/* ----------------------------- home: left-menu navigation ----------------------------- */
@Composable
fun HomeScreen(
    prefs: SharedPreferences,
    playlistName: String,
    source: Source?,
    data: AppData?,
    loadError: String?,
    catalogLoading: Boolean,
    catalogError: String?,
    onRetryCatalog: () -> Unit,
    activeIdx: Int,
    section: String,
    depth: Int,
    mini: MiniState?,
    onResumeMini: () -> Unit,
    onCloseMini: () -> Unit,
    onRoot: (String) -> Unit,
    onBackToRoot: () -> Unit,
    liveCat: String, onLiveCat: (String) -> Unit,
    movieCat: String, onMovieCat: (String) -> Unit,
    seriesCat: String, onSeriesCat: (String) -> Unit,
    searchQuery: String, onSearchQuery: (String) -> Unit,
    playlists: List<Playlist>,
    onSelectPlaylist: (Int) -> Unit,
    onDeletePlaylist: (Int) -> Unit,
    onAddPlaylist: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Playable) -> Unit,
    onPlayLive: (List<Playable>, Int) -> Unit,
    onSeries: (SeriesItem) -> Unit
) {
    // Remote's Back button climbs out one level instead of leaving the app.
    BackHandler(enabled = depth == 1) { onBackToRoot() }

    // At the main menu, Back asks before actually closing the app —
    // no more accidental exits from one extra button press.
    val activity = LocalContext.current as? android.app.Activity
    var showExit by remember { mutableStateOf(false) }
    BackHandler(enabled = depth == 0) { showExit = true }
    val exitStayFocus = remember { FocusRequester() }
    LaunchedEffect(showExit) {
        if (showExit) {
            kotlinx.coroutines.delay(100)
            runCatching { exitStayFocus.requestFocus() }
        }
    }
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            containerColor = SurfaceCol,
            title = { Text("Leave EZTV?", color = Ink) },
            text = {
                Text(
                    "Downloads in progress and scheduled DVR recordings keep working in the background even after you exit — the device just needs to stay powered on.",
                    color = Muted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)),
                    onClick = { activity?.finish() }
                ) { Text("Exit", color = Accent) }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.focusRequester(exitStayFocus).tvFocus(RoundedCornerShape(18.dp)),
                    onClick = { showExit = false }
                ) { Text("Stay", color = Ink) }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("EZTV", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Accent, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(playlistName, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (Recorder.activeName.value != null) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = "Recording", tint = Live)
                Spacer(Modifier.width(8.dp))
            }
            var clock by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                val f = SimpleDateFormat("h:mm a", Locale.getDefault())
                while (true) {
                    clock = f.format(Date())
                    kotlinx.coroutines.delay(15_000)
                }
            }
            Text(clock, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        }

        when {
            data == null && loadError == null -> Box(Modifier.weight(1f)) { LoadingBox("Loading your playlist…") }
            loadError != null -> Box(Modifier.weight(1f)) { ErrorBox(loadError, onRetry) }
            else -> Row(Modifier.weight(1f)) {
                HomeRail(
                    data = data!!,
                    section = section,
                    depth = depth,
                    liveCat = liveCat,
                    movieCat = movieCat,
                    seriesCat = seriesCat,
                    onRoot = onRoot,
                    onBackToRoot = onBackToRoot,
                    onCat = { id ->
                        when (section) {
                            "live" -> onLiveCat(id)
                            "movies" -> onMovieCat(id)
                            else -> onSeriesCat(id)
                        }
                    }
                )
                Box(Modifier.weight(1f)) {
                    if (catalogLoading && (section == "movies" || section == "series" || section == "search")) {
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(10f)
                                .background(Color(0xDD171922), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Loading on-demand catalog…", color = Ink, fontSize = 12.sp)
                        }
                    }
                    when {
                        !catalogLoading && catalogError != null &&
                            (section == "movies" || section == "series") &&
                            data!!.movies.isEmpty() && data!!.series.isEmpty() ->
                            ErrorBox(
                                err = catalogError ?: "On-demand catalog request failed",
                                onRetry = onRetryCatalog,
                                title = "Couldn't load Movies & Series"
                            )
                        depth == 1 && section == "live" -> LivePane(prefs, activeIdx, data!!, liveCat, onPlayLive)
                        depth == 1 && section == "movies" -> MoviesPane(prefs, data!!, movieCat, onPlay)
                        depth == 1 && section == "series" -> SeriesPane(source, data!!, seriesCat, onSeries)
                        section == "search" -> SearchTab(
                            prefs, data!!, searchQuery, onSearchQuery, onPlay, onSeries,
                            onDemandWarning = if (!catalogLoading) catalogError else null
                        )
                        section == "downloads" -> DownloadsPane(prefs, onPlay)
                        section == "recordings" -> RecordingsPane(prefs, onPlay)
                        section == "playlists" -> PlaylistsPane(playlists, activeIdx, onSelectPlaylist, onDeletePlaylist, onAddPlaylist)
                        section == "settings" -> SettingsPane(prefs, onModeChanged = onRetry)
                        else -> Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Pick a section on the left.", color = Muted, fontSize = 14.sp)
                        }
                    }
                }
                // The SAME stream you were watching, showing in the corner while
                // you browse. Highlight it and press OK to go back full screen —
                // nothing reloads, because it's one continuous stream.
                if (mini != null && Playback.player != null) {
                    val mp = mini.queue.getOrNull(Playback.currentIdxC.intValue)
                        ?: mini.queue.getOrNull(mini.index)
                    if (mp != null) {
                        Column(
                            Modifier
                                .width(236.dp)
                                .fillMaxHeight()
                                .padding(start = 4.dp, end = 10.dp, top = 6.dp)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .tvFocus(RoundedCornerShape(10.dp))
                                    .clickable { onResumeMini() }
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black)
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                player = Playback.player
                                                useController = false
                                                isFocusable = false
                                                isFocusableInTouchMode = false
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            }
                                        },
                                        update = { it.player = Playback.player },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Phones: the video view eats touches, so this
                                    // invisible layer catches the tap. (TV remotes
                                    // use the focus ring + OK on the outer box.)
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .pointerInput(Unit) {
                                                detectTapGestures {
                                                    onResumeMini()
                                                }
                                            }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                mp.name,
                                color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Text("Press OK on the picture for full screen.", color = Muted, fontSize = 10.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier
                                    .tvFocus(RoundedCornerShape(8.dp))
                                    .background(Surface2, RoundedCornerShape(8.dp))
                                    .clickable { onCloseMini() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop playing", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val RootItems = listOf(
    "live" to "Live TV",
    "movies" to "Movies",
    "series" to "Series",
    "search" to "Search",
    "downloads" to "Downloads",
    "recordings" to "Recordings",
    "playlists" to "Playlists",
    "settings" to "Settings"
)

/* The whole app steers from this left menu: OK goes deeper, Back climbs out. */
@Composable
private fun HomeRail(
    data: AppData,
    section: String,
    depth: Int,
    liveCat: String,
    movieCat: String,
    seriesCat: String,
    onRoot: (String) -> Unit,
    onBackToRoot: () -> Unit,
    onCat: (String) -> Unit
) {
    // Simple Mode changes the LIVE playback engine only. Movies, Series,
    // Search, Downloads, and saved Recordings remain available.
    val firstRootFocus = remember { FocusRequester() }
    LaunchedEffect(depth) {
        if (depth == 0) {
            kotlinx.coroutines.delay(100)
            runCatching { firstRootFocus.requestFocus() }
        }
    }
    LazyColumn(
        modifier = Modifier.width(126.dp).fillMaxHeight().background(SurfaceCol),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        if (depth == 0) {
            itemsIndexed(RootItems) { pos, p ->
                RailItem(
                    p.second, section == p.first,
                    modifier = if (pos == 0) Modifier.focusRequester(firstRootFocus) else Modifier
                ) { onRoot(p.first) }
            }
        } else {
            item { RailItem("←  Main menu", false) { onBackToRoot() } }
            val cats: List<Category>
            val extras: List<Pair<String, String>>
            val selected: String
            when (section) {
                "live" -> {
                    cats = data.liveCats
                    extras = listOf("fav" to "★ Favorites", "all" to "All channels")
                    selected = liveCat
                }
                "movies" -> {
                    cats = data.vodCats
                    extras = listOf("all" to "All movies")
                    selected = movieCat
                }
                else -> {
                    cats = data.seriesCats
                    extras = listOf("all" to "All series")
                    selected = seriesCat
                }
            }
            items(extras) { p ->
                RailItem(p.second, selected == p.first) { onCat(p.first) }
            }
            items(cats) { c ->
                RailItem(c.name, selected == c.id) { onCat(c.id) }
            }
        }
    }
}

@Composable
private fun RailItem(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocus(RoundedCornerShape(8.dp))
            .background(if (active) Surface2 else SurfaceCol)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(3.dp).height(18.dp)
                .background(if (active) Accent else Color.Transparent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (active) Ink else Muted,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp)
        )
    }
}

@Composable
private fun LoadingBox(msg: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Accent)
        Spacer(Modifier.height(16.dp))
        Text(msg, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorBox(err: String, onRetry: () -> Unit, title: String = "Couldn't load your playlist") {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Check your service connection, then try again. Details: $err",
            fontSize = 13.sp, color = Muted
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.tvFocus(RoundedCornerShape(26.dp))) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
    }
}

/* ----------------------------- live pane (with built-in guide) ----------------------------- */
@Composable
fun LivePane(
    prefs: SharedPreferences,
    activeIdx: Int,
    data: AppData,
    selectedCat: String,
    onPlayLive: (List<Playable>, Int) -> Unit
) {
    val context = LocalContext.current
    val favKey = "fav_live_$activeIdx"
    var favs by remember(activeIdx) { mutableStateOf(prefs.getStringSet(favKey, emptySet())?.toSet() ?: emptySet()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val guideLoading = EpgStore.loading.value
    val guideReady = EpgStore.loaded.value

    fun toggleFav(id: String) {
        val n = favs.toMutableSet()
        if (!n.add(id)) n.remove(id)
        favs = n
        prefs.edit().putStringSet(favKey, n).apply()
    }

    val filtered = data.live.filter { c ->
        when (selectedCat) {
            "all" -> true
            "fav" -> favs.contains(c.id)
            else -> c.categoryId == selectedCat
        }
    }

    // Come back to Live TV and the list is scrolled right where you left it.
    val listState = androidx.compose.runtime.saveable.rememberSaveable(
        selectedCat, saver = androidx.compose.foundation.lazy.LazyListState.Saver
    ) { androidx.compose.foundation.lazy.LazyListState() }

    // Cable-box behavior: opening the guide puts the highlighter ON the channel
    // you're currently watching, scrolled into view.
    val currentUrl = remember { prefs.getString("last_live_url", null) }
    val currentIdxInList = remember(filtered, currentUrl) {
        if (currentUrl == null) -1 else filtered.indexOfFirst { it.url == currentUrl }
    }
    val currentRowFocus = remember { FocusRequester() }
    LaunchedEffect(selectedCat) {
        if (currentIdxInList >= 0) {
            kotlinx.coroutines.delay(120)
            runCatching { listState.scrollToItem(currentIdxInList) }
            kotlinx.coroutines.delay(120)
            runCatching { currentRowFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (guideLoading) {
            Text(
                "Downloading TV guide… this can take a minute or two.",
                fontSize = 11.sp, color = Muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        if (filtered.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (selectedCat == "fav") "No favorites yet — tap a star." else "No channels here.",
                    color = Muted, fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filtered) { chIdx, ch ->
                    val schedule = if (guideReady) EpgStore.guide(ch.epgId, ch.name) else emptyList()
                    val now = System.currentTimeMillis()
                    val current = schedule.firstOrNull { now in it.startMs until it.endMs }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (chIdx == currentIdxInList) Modifier.focusRequester(currentRowFocus)
                                else Modifier
                            )
                            .tvFocus()
                            .background(SurfaceCol, RoundedCornerShape(14.dp))
                            .clickable {
                                // Hand the player this WHOLE category, starting on this
                                // channel — that's what makes channel up/down work.
                                val q = filtered.map { livePlayable(prefs, it) }
                                onPlayLive(q, filtered.indexOfFirst { it.id == ch.id }.coerceAtLeast(0))
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelIcon(ch.name, ch.icon)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ch.name,
                                    color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (current != null) {
                                    Text(
                                        "${fmt.format(Date(current.startMs))}–${fmt.format(Date(current.endMs))}  •  ${current.title}",
                                        color = Accent, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (schedule.isNotEmpty()) {
                                IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                                    expandedId = if (expandedId == ch.id) null else ch.id
                                }) {
                                    Icon(
                                        Icons.Filled.Today,
                                        contentDescription = "See what's on later",
                                        tint = if (expandedId == ch.id) Accent else Muted
                                    )
                                }
                            }
                            IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = { toggleFav(ch.id) }) {
                                Icon(
                                    if (favs.contains(ch.id)) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (favs.contains(ch.id)) Accent else Muted
                                )
                            }
                        }
                        if (expandedId == ch.id && schedule.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            val dayFmt = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
                            schedule.take(30).forEach { e ->
                                val isNow = now in e.startMs until e.endMs
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        dayFmt.format(Date(e.startMs)),
                                        fontSize = 12.sp,
                                        color = if (isNow) Accent else Muted,
                                        modifier = Modifier.width(96.dp)
                                    )
                                    Text(
                                        (if (isNow) "NOW  •  " else "") + e.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isNow) Ink else Muted,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (ch.url.endsWith(".ts")) {
                                        IconButton(
                                            modifier = Modifier.size(32.dp).tvFocus(RoundedCornerShape(16.dp)),
                                            onClick = {
                                                if (isNow) {
                                                    val spaceMsg = Recorder.spaceCheck(context)
                                                    if (spaceMsg != null && !spaceMsg.startsWith("WARN:")) {
                                                        toast(context, spaceMsg)
                                                    } else {
                                                        if (spaceMsg != null) toast(context, spaceMsg.removePrefix("WARN:"))
                                                        Recorder.start(context, ch.url, "${e.title} (${ch.name})", e.endMs + 2 * 60 * 1000)
                                                        toast(context, "Recording \"${e.title}\" until it ends.")
                                                    }
                                                } else {
                                                    toast(context, ScheduleStore.add(context, prefs, e.title, ch.name, ch.url, e.startMs, e.endMs))
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Filled.FiberManualRecord,
                                                contentDescription = if (isNow) "Record now" else "Schedule recording",
                                                tint = Live
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* The timeshift DVR records the classic (.ts) live stream — the one every
 * provider supports and the only one that can be recorded. */
private fun tsUrl(url: String): String =
    if (url.endsWith(".m3u8")) url.removeSuffix(".m3u8") + ".ts" else url

private fun liveAutoUrl(prefs: SharedPreferences, url: String): String = url

private fun livePlayable(prefs: SharedPreferences, ch: LiveChannel): Playable {
    return Playable(
        name = ch.name,
        url = liveAutoUrl(prefs, ch.url),
        isLive = true,
        epgId = ch.id,
        guideKey = ch.epgId,
        canRecord = ch.url.endsWith(".ts"),
        artwork = ch.icon
    )
}

/* ---------------------------------------------------------------------------
 * TIMESHIFT — real DVR pause for live TV.
 * While you watch a live channel, this engine continuously records the incoming
 * stream to a file on the device, and the player watches FROM THE FILE — never
 * straight from the internet. So:
 *  - Pause for the bathroom → the recorder keeps recording → play resumes at
 *    the exact same spot, now safely behind live.
 *  - Internet hiccup → it only hits the recorder; as long as you're behind
 *    live at all, the picture never stutters. The recorder even quietly
 *    reconnects on its own if the provider drops it.
 * The file lives in the app's cache and is wiped on channel change and exit.
 * ------------------------------------------------------------------------- */
/* Recent live channels, tracked by IDENTITY (url), app-wide — so the list
 * survives leaving the player and works across categories. Updated only on a
 * successful lock-in (an event, never a tick), read only when the mini guide
 * is open. (Panel bug fix: the old list lived inside the player screen and
 * stored positions, so it wiped on exit and broke across categories.) */
internal object RecentChannels {
    private const val KEY = "recent_live_urls_v417"
    val items = androidx.compose.runtime.mutableStateListOf<Playable>()

    /** Restore history against the CURRENT playlist so stale/deleted channels
     * simply vanish. This runs when playlist data arrives, never during video. */
    fun restore(prefs: SharedPreferences, candidates: List<Playable>) {
        val urls = prefs.getString(KEY, "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
        if (urls.isEmpty()) return
        val byUrl = candidates.associateBy { it.url }
        items.clear()
        urls.mapNotNullTo(items) { byUrl[it] }
        while (items.size > 7) items.removeAt(items.size - 1)
    }

    /** A channel becomes recent only after it actually reaches READY. One tiny
     * preference write per successful tune is negligible and survives restarts. */
    fun push(p: Playable, prefs: SharedPreferences? = null) {
        items.removeAll { it.url == p.url }
        items.add(0, p)
        while (items.size > 7) items.removeAt(items.size - 1)
        prefs?.edit()?.putString(KEY, items.joinToString("\n") { it.url })?.apply()
    }
}

internal object Timeshift {
    @Volatile var bytesWritten: Long = 0L
    @Volatile var active: Boolean = false
    @Volatile var file: File? = null
    /** Monotonic start of this channel's temporary DVR window. Used only for
     * the on-demand mini-guide timeline; no periodic background task. */
    @Volatile var startedAtElapsedMs: Long = 0L
        private set
    @Volatile var startedAtWallMs: Long = 0L
        private set

    fun windowMs(): Long = if (startedAtElapsedMs > 0L)
        (android.os.SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)
    else 0L

    // ---- writer-flow diagnostics (panel: watch the WRITER, not just the player) ----
    @Volatile var lastByteAt: Long = 0L          // when the last provider byte arrived
    @Volatile var throughputBps: Double = 0.0    // (legacy; kept for the dead-feed timing)

    @Volatile private var gen = 0L
    @Volatile private var currentCall: okhttp3.Call? = null
    /** Append-only DVR safety cap. Internal eMMC stays deliberately small; a
     * verified removable drive gets a larger (<4 GiB) cap that remains safe on
     * FAT32-like volumes. When the cap is reached Playback falls back to direct
     * live instead of freezing at the end of the file. */
    @Volatile var capBytes: Long = 1_000_000_000L
        private set
    @Volatile var hitCap: Boolean = false
        private set

    /** Find the first verified TS packet boundary in a buffer: three 0x47 sync
     *  bytes exactly 188 apart. Returns the offset, or -1 if none found. */
    private fun findTsSync(b: ByteArray, len: Int): Int {
        var i = 0
        while (i + 376 < len) {
            if (b[i] == 0x47.toByte() && b[i + 188] == 0x47.toByte() && b[i + 376] == 0x47.toByte()) return i
            i++
        }
        return -1
    }

    /** MPEG-TS packets are exactly 188 bytes, sync byte 0x47. The writer only
     *  ever publishes WHOLE packets (see the carry buffer in start()), so the
     *  file is always packet-aligned and reconnect seams are clean. */
    @Synchronized
    fun start(context: Context, url: String, prefs: SharedPreferences? = null) {
        stopInternal()
        val dir = if (prefs != null) Storage.timeshiftDir(context, prefs) else context.cacheDir
        val f = File(dir, "timeshift.ts")
        runCatching { f.delete() }
        file = f
        bytesWritten = 0L
        hitCap = false
        capBytes = if (prefs != null && Storage.usingDrive(context, prefs)) 3_500_000_000L else 1_000_000_000L
        startedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        startedAtWallMs = System.currentTimeMillis()
        active = true
        lastByteAt = System.currentTimeMillis()
        throughputBps = 0.0
        val myGen = ++gen
        Thread {
            while (active && gen == myGen && bytesWritten < capBytes) {
                try {
                    val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                    val c = Net.streamClient.newCall(req)
                    currentCall = c
                    c.execute().use { resp ->
                        val inp = resp.body?.byteStream()
                        if (inp != null) {
                            java.io.FileOutputStream(f, true).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var sinceCheck = 0L
                                // SEAM RULE (v4.16): only WHOLE 188-byte packets
                                // ever reach the published file. Partial packets
                                // wait in this carry buffer until completed by
                                // the next read. The file length is therefore
                                // ALWAYS packet-aligned — no truncation on
                                // disconnect, and the localhost reader position
                                // can never end up mid-packet after a reconnect.
                                val carry = ByteArray(188)
                                var carryLen = 0
                                var aligned = false
                                var pend = java.io.ByteArrayOutputStream()

                                fun writePackets(data: ByteArray, off0: Int, len0: Int) {
                                    var off = off0
                                    var len = len0
                                    // Complete a partial packet from last read.
                                    if (carryLen > 0) {
                                        val need = 188 - carryLen
                                        if (len < need) {
                                            System.arraycopy(data, off, carry, carryLen, len)
                                            carryLen += len
                                            return
                                        }
                                        System.arraycopy(data, off, carry, carryLen, need)
                                        out.write(carry, 0, 188)
                                        bytesWritten += 188
                                        carryLen = 0
                                        off += need
                                        len -= need
                                    }
                                    // Write all whole packets; keep the tail.
                                    val whole = (len / 188) * 188
                                    if (whole > 0) {
                                        out.write(data, off, whole)
                                        bytesWritten += whole
                                    }
                                    val rem = len - whole
                                    if (rem > 0) {
                                        System.arraycopy(data, off + whole, carry, 0, rem)
                                        carryLen = rem
                                    }
                                }

                                while (active && gen == myGen && bytesWritten < capBytes) {
                                    val n = inp.read(buf)
                                    if (n < 0) break
                                    if (!active || gen != myGen) break
                                    if (!aligned) {
                                        // Find a verified packet boundary once per
                                        // connection, then stream through the
                                        // whole-packet writer from there.
                                        pend.write(buf, 0, n)
                                        val pb = pend.toByteArray()
                                        val sync = findTsSync(pb, pb.size)
                                        if (sync >= 0) {
                                            aligned = true
                                            writePackets(pb, sync, pb.size - sync)
                                            pend = java.io.ByteArrayOutputStream()
                                        } else if (pb.size > 8192) {
                                            val keep = pb.copyOfRange(pb.size - 512, pb.size)
                                            pend = java.io.ByteArrayOutputStream()
                                            pend.write(keep)
                                        }
                                        continue
                                    }
                                    writePackets(buf, 0, n)
                                    // Lightweight flow tracking for the dead-feed check.
                                    lastByteAt = System.currentTimeMillis()
                                    // Storage floor: never squeeze the device.
                                    sinceCheck += n
                                    if (sinceCheck > 32_000_000) {
                                        sinceCheck = 0
                                        val free = runCatching {
                                            android.os.StatFs(f.parentFile!!.absolutePath).availableBytes
                                        }.getOrDefault(Long.MAX_VALUE)
                                        if (free < 1_500_000_000L) break
                                    }
                                }
                                // Connection over: the carry remainder is simply
                                // dropped — it was never visible to the reader.
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Connection dropped or cancelled — fall through.
                }
                if (active && gen == myGen) {
                    try { Thread.sleep(1_000) } catch (e: InterruptedException) { break }
                }
            }
            if (gen == myGen) {
                if (bytesWritten >= capBytes) hitCap = true
                active = false
            }
        }.apply { isDaemon = true; name = "timeshift-writer" }.start()
    }

    @Synchronized
    fun stop() {
        stopInternal()
        runCatching { file?.delete() }
        file = null
    }

    private fun stopInternal() {
        active = false
        gen++
        bytesWritten = 0L
        hitCap = false
        startedAtElapsedMs = 0L
        startedAtWallMs = 0L
        // Sever the old provider connection IMMEDIATELY. Without this, a stale
        // downloader could sit on a dead provider socket for minutes — the
        // provider then sees ghost connections stack up and throttles the
        // account, which is why playback used to get worse the longer you
        // channel-surfed. Cancel = connection closed, thread exits, all clean.
        runCatching { currentCall?.cancel() }
        currentCall = null
    }
}

/* Serves the growing DVR file to the player as a plain localhost stream —
 * the exact same kind of stream the player already plays perfectly from
 * providers. When playback reaches the end of what's recorded so far, the
 * server simply waits for more before sending it. No custom player internals. */
private object TimeshiftServer {
    @Volatile var port = 0
    private var server: java.net.ServerSocket? = null

    @Synchronized
    fun ensureStarted() {
        if (server != null) return
        // Android forbids socket work on the main thread — bind on a worker and
        // wait briefly for the port (binding to localhost is instant).
        val latch = java.util.concurrent.CountDownLatch(1)
        Thread {
            try {
                val ss = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
                server = ss
                port = ss.localPort
                latch.countDown()
                while (true) {
                    val sock = try { ss.accept() } catch (e: Exception) { break }
                    Thread { handle(sock) }.apply { isDaemon = true }.start()
                }
            } catch (e: Exception) {
                latch.countDown()
            }
        }.apply { isDaemon = true; name = "tshift-server" }.start()
        runCatching { latch.await(2, java.util.concurrent.TimeUnit.SECONDS) }
    }

    private fun handle(sock: java.net.Socket) {
        try {
            sock.tcpNoDelay = true
            val reader = java.io.BufferedReader(java.io.InputStreamReader(sock.getInputStream()))
            val requestLine = reader.readLine() ?: return
            var rangeStart = 0L
            var hadRange = false
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    hadRange = true
                    val raw = line.substringAfter("=").substringBefore("-").trim()
                    rangeStart = raw.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                }
            }

            val virtualLength = Timeshift.capBytes.coerceAtLeast(1L)
            if (rangeStart >= virtualLength) rangeStart = (virtualLength - 1).coerceAtLeast(0L)
            val partial = hadRange
            val out = java.io.BufferedOutputStream(sock.getOutputStream())
            val headers = buildString {
                if (partial) append("HTTP/1.1 206 Partial Content\r\n")
                else append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: video/mp2t\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Cache-Control: no-store\r\n")
                // A growing stream normally has unknown length, which makes a
                // Progressive TS source unseekable. EZTV's DVR has a hard cap,
                // so expose that cap as a safe VIRTUAL length. The server still
                // tail-follows unwritten bytes. Media3 can now issue Range seeks
                // into the already-recorded part without changing the DVR writer.
                append("Content-Length: ${virtualLength - rangeStart}\r\n")
                if (partial) append("Content-Range: bytes $rangeStart-${virtualLength - 1}/$virtualLength\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(headers.toByteArray())
            out.flush()

            val myFile = Timeshift.file ?: return
            java.io.RandomAccessFile(myFile, "r").use { raf ->
                var pos = rangeStart
                val buf = ByteArray(64 * 1024)
                var idleTicks = 0
                while (true) {
                    if (Timeshift.file !== myFile) break
                    val real = minOf(Timeshift.bytesWritten, raf.length())
                    val avail = real - pos
                    if (avail > 0) {
                        idleTicks = 0
                        raf.seek(pos)
                        val want = if (buf.size.toLong() < avail) buf.size else avail.toInt()
                        val n = raf.read(buf, 0, want)
                        if (n > 0) {
                            out.write(buf, 0, n)
                            out.flush()
                            pos += n
                        } else {
                            Thread.sleep(50)
                        }
                    } else if (!Timeshift.active) {
                        break
                    } else {
                        Thread.sleep(50)
                        if (++idleTicks >= 40) {
                            idleTicks = 0
                            val gone = try {
                                sock.soTimeout = 1
                                sock.getInputStream().read() == -1
                            } catch (t: java.net.SocketTimeoutException) {
                                false
                            } catch (e: Exception) {
                                true
                            }
                            if (gone) break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Client hung up (seek/channel change/app exit) — normal.
        } finally {
            runCatching { sock.close() }
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        port = 0
    }
}

/* ---------------------------------------------------------------------------
 * PLAYBACK STREAM OWNERSHIP.
 * There is still exactly ONE ExoPlayer. The viewer chooses an IPTV-provider
 * connection budget of 1, 2, or 3 in Settings. Ordinary playback consumes one
 * remote slot; same-channel DVR recording tees from the existing timeshift and
 * costs zero extra; only independent recording/download requests consume extra
 * provider connections. Full-screen and corner views share the same player.
 * ------------------------------------------------------------------------- */
@OptIn(UnstableApi::class)
object Playback {
    var player: ExoPlayer? = null
        private set
    var queue: List<Playable> = emptyList()
        private set
    /** True when live TV is playing through the timeshift DVR file. */
    var liveMode: Boolean = false
        private set

    private var appContext: Context? = null
    private var prefsRef: SharedPreferences? = null

    // Compose-observable playback state, shared by every screen.
    val currentIdxC = androidx.compose.runtime.mutableIntStateOf(0)
    val playStateC = androidx.compose.runtime.mutableIntStateOf(Player.STATE_IDLE)
    val streamDeadC = androidx.compose.runtime.mutableStateOf(false)
    val everReadyC = androidx.compose.runtime.mutableStateOf(false)
    val videoFpsC = androidx.compose.runtime.mutableFloatStateOf(0f)

    private var standardMediaSources: androidx.media3.exoplayer.source.DefaultMediaSourceFactory? = null
    private var tolerantTsMediaSources: androidx.media3.exoplayer.source.DefaultMediaSourceFactory? = null
    private var browserVodMediaSources: androidx.media3.exoplayer.source.DefaultMediaSourceFactory? = null
    private var vodUaFallbackUsed = false

    private fun mediaItemFor(pl: Playable, forceClassic: Boolean): MediaItem {
        val u = if (forceClassic && pl.isLive) tsUrl(pl.url) else pl.url
        val uri = if (u.startsWith("/")) Uri.fromFile(File(u)) else Uri.parse(u)
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(pl.name).build())
            .build()
    }

    private fun ensurePlayer(context: Context, prefs: SharedPreferences): ExoPlayer {
        appContext = context.applicationContext
        prefsRef = prefs
        simpleRaw = prefs.getBoolean("simple_mode", true)
        player?.let { return it }
        val bufferSec = prefs.getInt("buffer_sec", 30)
        // How much video to collect before showing the picture (and 2x that
        // after a stall). Bigger = slower channel changes but steadier playback
        // on weak channels. Settings › "Channel lock-in cushion".
        // SIMPLE MODE removes DVR/recording/governor overhead, but it is meant
        // to HELP a troublesome channel — not race to picture with only 1.5 s.
        // Keep the user's normal lock-in cushion so the raw provider path still
        // has a few seconds of protection before playback begins.
        val lockMs = prefs.getInt("live_start_ms", 4_000).coerceIn(2_000, 12_000)
        val renderersFactory = DefaultRenderersFactory(context)
            // Keep the bundled FFmpeg decoder AVAILABLE as fallback, but let Fire TV
            // hardware/native audio decode first to save CPU during live playback.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        // LIVE TV REALITY: providers send live video at exactly real-time speed,
        // so a live buffer can never stockpile much — the only cushion you get
        // is what you collect BEFORE playing. Start with ~4s in the tank, and
        // after any stall rebuild ~8s before resuming, so every stall comes back
        // more protected than before. (The old 1.5s "fast start" drained on the
        // first network dip and caused a stall loop.)
        val steadyRecovery = prefs.getBoolean("live_steady_recovery", false)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                (bufferSec * 1000).coerceAtMost(60_000),
                (bufferSec * 1000 * 3).coerceIn(60_000, 90_000),
                lockMs,                                    // collect the chosen cushion before starting
                if (steadyRecovery) (lockMs * 2).coerceAtLeast(6_000) else lockMs
            )
            .setBackBuffer(10_000, false)
            // Let Media3 size the byte target from the track (panel: the fixed
            // 24MB cap could stop loading before enough SECONDS were banked,
            // which showed up as the buffer % filling very slowly). Time is the
            // controlling constraint, exactly like the smooth 4.9 build.
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        // Some IPTV MPEG-TS streams carry CEA-608 captions without declaring
        // them in PMT metadata. Tell Media3 to expose channel 1 when present so
        // the CC toggle can actually select those captions. This adds no polling.
        val tsCaptionFormats = listOf(
            androidx.media3.common.Format.Builder()
                .setSampleMimeType(androidx.media3.common.MimeTypes.APPLICATION_CEA608)
                .setAccessibilityChannel(1)
                .build()
        )
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setTsSubtitleFormats(tsCaptionFormats)
        val tolerantTsExtractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setTsSubtitleFormats(tsCaptionFormats)
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
            )
        // Use one explicit HTTP policy for direct live + VOD. Media3's default
        // HTTP source times out reads after 8 s, uses a platform UA, and rejects
        // HTTP<->HTTPS redirects. IPTV providers commonly redirect stream URLs,
        // and a weak live server can pause longer than 8 s without truly dying.
        // Keep this passive: no worker/tick is added; it only changes socket rules.
        val mediaHttp = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(Net.UA)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val mediaData = androidx.media3.datasource.DefaultDataSource.Factory(context, mediaHttp)

        // Normal/live uses the cheap extractor path. Only MPEG-TS VOD gets the
        // more tolerant (and more CPU-expensive) parser used for malformed files.
        standardMediaSources = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(mediaData, extractors)
            .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(8))
        tolerantTsMediaSources = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(mediaData, tolerantTsExtractors)
            .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(8))
        // Some IPTV VOD hosts accept the Xtream/API login but reject a custom
        // player User-Agent with 403/406. Keep one passive browser-UA fallback
        // and use it ONLY after Media3 proves that exact response code.
        val browserHttp = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val browserData = androidx.media3.datasource.DefaultDataSource.Factory(context, browserHttp)
        browserVodMediaSources = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(browserData, tolerantTsExtractors)
            .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(8))
        val p = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(standardMediaSources!!)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setLoadControl(loadControl)
            .build()
        // Proper audio focus: when the customer leaves for the Fire TV home
        // screen or another app grabs the speakers, Android pauses us
        // automatically — no more channel audio haunting the main menu.
        p.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        applyCaptionPreference(p, prefs.getBoolean("cc_enabled", false))
        p.addListener(object : Player.Listener {
            private var prevIdx = 0

            override fun onPlaybackStateChanged(playbackState: Int) {
                playStateC.intValue = playbackState
                if (playbackState == Player.STATE_BUFFERING) noteBufferingStarted()
                if (playbackState == Player.STATE_READY) {
                    retriesP = 0
                    streamDeadC.value = false
                    everReadyC.value = true
                    noteReadyForStall()
                    if (liveMode) noteLiveReady()
                } else if (playbackState == Player.STATE_ENDED && liveMode && queue.isNotEmpty()) {
                    // Some IPTV servers close a live HTTP response cleanly instead
                    // of throwing an error. Treat that as a reconnect event so the
                    // picture does not sit paused waiting for the customer to press Play.
                    val myGen = playbackGen
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (myGen == playbackGen && liveMode && player === p) {
                            zapTo(currentIdxC.intValue, preserveDirect = directLive)
                        }
                    }, 700)
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val fps = p.videoFormat?.frameRate ?: 0f
                if (fps > 0f) videoFpsC.floatValue = fps
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // VOD-only compatibility retry: if the provider explicitly
                // rejects our normal UA with 403/406, rebuild the SAME queue
                // once with a browser-style UA. No polling and no live-TV cost.
                if (!liveMode && !vodUaFallbackUsed) {
                    var cause: Throwable? = error
                    var httpCode: Int? = null
                    while (cause != null) {
                        if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                            httpCode = cause.responseCode
                            break
                        }
                        cause = cause.cause
                    }
                    if (httpCode == 403 || httpCode == 406) {
                        vodUaFallbackUsed = true
                        val idx = p.currentMediaItemIndex.coerceAtLeast(0)
                        val pos = p.currentPosition.coerceAtLeast(0L)
                        val sources = queue.map { pl -> browserVodMediaSources!!.createMediaSource(mediaItemFor(pl, false)) }
                        p.setMediaSources(sources, idx.coerceAtMost((sources.size - 1).coerceAtLeast(0)), pos)
                        p.prepare()
                        p.playWhenReady = true
                        return
                    }
                }
                if (retriesP >= 6) {
                    streamDeadC.value = true
                    return
                }
                retriesP++
                val wait = (1_000L * retriesP).coerceAtMost(5_000L)
                // Generation guard (panel bug fix): if the viewer changes
                // channel before this delayed retry fires, the retry belongs to
                // a DEAD session — firing it would restart the NEW channel and
                // look exactly like random buffering. Stale retries do nothing.
                val myGen = playbackGen
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (myGen != playbackGen) return@postDelayed
                    runCatching {
                        if (liveMode) {
                            // Timeshift trouble? After 3 strikes, flip to direct
                            // provider playback so video ALWAYS works.
                            noteLiveFail()
                            zapTo(currentIdxC.intValue, preserveDirect = directLive)
                        } else {
                            p.prepare()
                            p.play()
                        }
                    }
                }, wait)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (liveMode) return   // live channel changes are driven by zapTo()
                val q = queue
                val prev = prevIdx
                prevIdx = p.currentMediaItemIndex
                currentIdxC.intValue = p.currentMediaItemIndex
                everReadyC.value = false
                videoFpsC.floatValue = 0f
                p.setPlaybackSpeed(1.0f)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    prev in q.indices && !q[prev].isLive
                ) {
                    WatchStore.markWatched(prefs, q[prev].url)
                }
            }
        })
        player = p
        return p
    }

    // -----------------------------------------------------------------------
    // THE CATCH-UP GOVERNOR — the fix for "it keeps catching up to the buffer."
    // Live video arrives at exactly 1.0x real-time. If we also PLAY at exactly
    // 1.0x, the cushion can only ever shrink — every network dip stalls it.
    // So: whenever the cushion gets thin, play at 0.95x (5% slower — nobody can
    // see or hear it) so the cushion refills WHILE you watch. Once it's healthy
    // again, back to full speed. Playback can never "catch up to the buffer."
    // -----------------------------------------------------------------------
    private val governor = android.os.Handler(android.os.Looper.getMainLooper())
    private var governorRunning = false
    private var lastBytesSeen = -1L
    private var lastBytesAt = 0L
    private var bufferingSince = 0L
    private var stallRestarts = 0
    // Progress tracking: a stall only counts when growth has STOPPED.
    private var lastBufMs = -1L
    private var lastBufGrowthAt = 0L
    // Speed-governor hysteresis (panel: stop it oscillating).
    private var lastSpeedChangeAt = 0L

    internal fun noteBufferingStarted() { if (bufferingSince == 0L) bufferingSince = System.currentTimeMillis() }
    internal fun noteReadyForStall() {
        bufferingSince = 0L
        stallRestarts = 0
        lastBufMs = -1L
    }

    private val governorTick = object : Runnable {
        override fun run() {
            val p = player
            if (p == null) { governorRunning = false; return }
            // Simple Mode: the governor does nothing, so it doesn't even run.
            // (Belt and suspenders — startGovernor also refuses to schedule it.)
            if (simpleRaw) { governorRunning = false; return }
            runCatching {
                if (liveMode) {
                    // The append-only DVR must never turn into a hard stop. If
                    // its safety cap is reached, transparently continue the same
                    // channel on the direct provider path. Pause/rewind/record
                    // are unavailable until the next channel tune, but LIVE TV
                    // keeps playing instead of freezing at the end of the file.
                    if (!directLive && Timeshift.hitCap) {
                        directLive = true
                        zapTo(currentIdxC.intValue, preserveDirect = true)
                        return@runCatching
                    }
                    val now = System.currentTimeMillis()
                    val cushionMs = p.totalBufferedDuration

                    // Optional cushion assist. Default is NORMAL 1.0x playback:
                    // collect the lock-in buffer once, then leave speed alone.
                    // Customers with especially bursty providers can opt into a
                    // gentle 0.95x refill when the cushion gets dangerously thin.
                    val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
                    if (!steadyRecovery) {
                        if (p.playbackParameters.speed != 1.0f) p.setPlaybackSpeed(1.0f)
                    } else if (p.isPlaying) {
                        val cur = p.playbackParameters.speed
                        if (cushionMs < 4_000 && cur > 0.96f) p.setPlaybackSpeed(0.95f)
                        else if (cushionMs > 6_000 && cur < 1.0f) p.setPlaybackSpeed(1.0f)
                    }

                    // Progress-based stall recovery (light): only act when the
                    // buffer has been frozen several seconds while buffering.
                    if (steadyRecovery && playStateC.intValue == Player.STATE_BUFFERING && bufferingSince > 0) {
                        if (cushionMs > lastBufMs + 200 || lastBufMs < 0) {
                            lastBufMs = cushionMs
                            lastBufGrowthAt = now
                        }
                        val frozenFor = now - lastBufGrowthAt
                        val bufferingFor = now - bufferingSince
                        val bytesFlowing = directLive ||
                            (now - Timeshift.lastByteAt) < 3_000
                        if (frozenFor > 4_000 && bufferingFor > 5_000) {
                            when {
                                !bytesFlowing && stallRestarts == 0 -> lastBufGrowthAt = now
                                stallRestarts == 0 && !directLive && p.currentPosition > 12_000 -> {
                                    stallRestarts = 1
                                    bufferingSince = 0L; lastBufMs = -1L
                                    p.seekTo((p.currentPosition - 8_000).coerceAtLeast(0))
                                    p.play()
                                }
                                stallRestarts <= 1 -> {
                                    stallRestarts = 2
                                    bufferingSince = 0L; lastBufMs = -1L
                                    zapTo(currentIdxC.intValue, preserveDirect = directLive)
                                }
                                else -> streamDeadC.value = true
                            }
                        }
                    }

                    // Dead-feed watchdog: zero bytes 20s while buffering = down.
                    if (!directLive) {
                        val b = Timeshift.bytesWritten
                        if (b != lastBytesSeen) { lastBytesSeen = b; lastBytesAt = now }
                        else if (now - lastBytesAt > 20_000 &&
                            playStateC.intValue == Player.STATE_BUFFERING) {
                            streamDeadC.value = true
                        }
                    }
                } else if (p.playbackParameters.speed != 1.0f) {
                    p.setPlaybackSpeed(1.0f)
                }
            }
            // When the cushion is healthy there is no reason to wake the main
            // thread every two seconds. Check slowly; tighten back to 2 s only
            // while the buffer is thin or the player is actively buffering.
            val nextCheck = if (liveMode &&
                (playStateC.intValue == Player.STATE_BUFFERING ||
                    (player?.totalBufferedDuration ?: 0L) < 7_000L)) 2_000L else 6_000L
            governor.postDelayed(this, nextCheck)
        }
    }

    private fun startGovernor() {
        if (governorRunning) return
        // Simple Mode plays raw — the governor never runs, so no periodic work
        // wakes threads or allocates during Simple Mode playback.
        if (simpleRaw) return
        governorRunning = true
        governor.postDelayed(governorTick, 2_000)
    }

    private fun stopGovernor() {
        governorRunning = false
        governor.removeCallbacks(governorTick)
    }

    /** Current-channel rescue. If the DVR/timeshift path fails repeatedly, that
     * channel temporarily falls back to the direct provider path. A MANUAL
     * channel change, Try Again, mode change, or full release clears it so a
     * hidden rescue state can never masquerade as Simple Mode. */
    @Volatile var directLive = false
        private set
    /** Simple Mode: raw cable-box playback. Live plays the provider stream
     *  directly — no DVR file, no server, no governor, no recovery machinery.
     *  Glitches show raw, exactly like the dead-simple apps. */
    @Volatile var simpleRaw = false
        private set
    private var liveFails = 0
    private var retriesP = 0
    // Bumped on every channel change / retry / stop. Delayed callbacks
    // capture the value and refuse to run if it has moved on (stale).
    @Volatile private var playbackGen = 0L

    internal fun noteLiveReady() {
        liveFails = 0
        queue.getOrNull(currentIdxC.intValue)?.let { RecentChannels.push(it, prefsRef) }
    }
    internal fun noteLiveFail(): Boolean {
        liveFails++
        if (liveFails >= 3 && !directLive) {
            directLive = true
            return true
        }
        return false
    }

    /** The customer pressed Try Again: reset the strike counters and run the
     *  full rescue cycle from scratch — a real retry, not a dead click. */
    fun tryAgain() {
        playbackGen++
        retriesP = 0
        directLive = false
        streamDeadC.value = false
        val p = player ?: return
        runCatching {
            if (liveMode) {
                zapTo(currentIdxC.intValue)
            } else {
                p.prepare()
                p.play()
            }
        }
    }

    /** Change live channel: point the DVR recorder at the new channel and play
     *  its growing file via localhost. Wraps around the lineup like a cable box. */
    fun zapToChannel(target: Playable) {
        directLive = false
        val i = queue.indexOfFirst { it.url == target.url }
        if (i >= 0) zapTo(i)
        else {
            queue = queue + target
            zapTo(queue.size - 1)
        }
    }

    fun zapTo(idx: Int, preserveDirect: Boolean = false) {
        // A viewer-initiated channel change always gets a fresh attempt at the
        // selected mode. Only an INTERNAL retry is allowed to preserve the
        // current-channel direct-rescue state. This prevents a hidden rescue
        // from following the viewer around and looking like Simple Mode is
        // "stuck on" after the setting was turned off.
        if (!preserveDirect) directLive = false
        playbackGen++
        val p = player ?: return
        val ctx = appContext ?: return
        val q = queue
        if (q.isEmpty()) return
        val i = ((idx % q.size) + q.size) % q.size
        val ch = q[i]
        currentIdxC.intValue = i
        everReadyC.value = false
        videoFpsC.floatValue = 0f
        streamDeadC.value = false
        retriesP = 0
        bufferingSince = 0L
        lastBufMs = -1L
        lastBytesSeen = -1L
        lastBytesAt = System.currentTimeMillis()
        lastSpeedChangeAt = 0L
        p.setPlaybackSpeed(1.0f)
        val uri: Uri
        if (directLive || simpleRaw) {
            // Direct from the provider: the raw path. Simple Mode lives here on
            // purpose; directLive lands here as the automatic fallback.
            Timeshift.stop()
            uri = Uri.parse(tsUrl(ch.url))
        } else {
            TimeshiftServer.ensureStarted()
            Timeshift.start(ctx, tsUrl(ch.url), prefsRef)
            uri = Uri.parse("http://127.0.0.1:" + TimeshiftServer.port + "/live/" + System.nanoTime())
        }
        p.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
                .build()
        )
        p.prepare()
        p.playWhenReady = true
        // Remember this channel for next app start.
        prefsRef?.edit()
            ?.putString("last_live_name", ch.name)
            ?.putString("last_live_url", ch.url)
            ?.putString("last_live_epg", ch.epgId ?: "")
            ?.putString("last_live_guide", ch.guideKey ?: "")
            ?.apply()
    }

    /**
     * Open playback. attachOnly = true means "same stream, just show it to me"
     * (corner → full screen): nothing is reloaded, nothing reconnects.
     */
    fun open(
        context: Context,
        prefs: SharedPreferences,
        newQueue: List<Playable>,
        start: Int,
        startAtMs: Long?,
        attachOnly: Boolean
    ): ExoPlayer {
        val p = ensurePlayer(context, prefs)
        if (attachOnly && queue.isNotEmpty()) return p
        // Leaving a successfully watched live channel should make it immediately
        // available in the surf strip, even before the next channel reaches READY.
        if (liveMode && everReadyC.value) queue.getOrNull(currentIdxC.intValue)?.let { RecentChannels.push(it, prefsRef) }
        queue = newQueue
        val s = start.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        if (newQueue.getOrNull(s)?.isLive == true) {
            directLive = false
            // LIVE only: the lightweight governor may run (Simple Mode disables it).
            liveMode = true
            startGovernor()
            zapTo(s)
        } else {
            // Movies / episodes / downloads / recordings: no periodic live-TV
            // supervision at all. Keep the Fire Stick focused on decode/render.
            liveMode = false
            vodUaFallbackUsed = false
            stopGovernor()
            Timeshift.stop()
            currentIdxC.intValue = s
            everReadyC.value = false
            streamDeadC.value = false
            p.setPlaybackSpeed(1.0f)
            val sources = newQueue.map { pl ->
                // Use the tolerant extractor factory for ALL on-demand items. It
                // only costs extra when the content actually sniffs as MPEG-TS,
                // so providers that label TS with a .mp4/.mkv URL still benefit.
                tolerantTsMediaSources!!.createMediaSource(mediaItemFor(pl, forceClassic = false))
            }
            p.setMediaSources(sources, s, startAtMs ?: C.TIME_UNSET)
            p.prepare()
            p.playWhenReady = true
        }
        return p
    }

    /** True only when the current live picture is actually being fed by the
     * one DVR writer. `liveMode` alone is NOT enough (v4.17 could be in direct
     * rescue and create a 0-byte recording). */
    fun canTeeRecording(): Boolean = liveMode && !simpleRaw && !directLive && Timeshift.active && Timeshift.file != null

    /** Recording a direct-rescue live channel must return to the one-connection
     * DVR path first; otherwise a tee has no source and a second network stream
     * would violate the provider connection rule. */
    fun prepareCurrentForRecording(): Boolean {
        if (!liveMode || simpleRaw) return false
        if (!canTeeRecording()) {
            directLive = false
            zapTo(currentIdxC.intValue)
        }
        return Timeshift.active && Timeshift.file != null
    }

    @Volatile private var backgroundSuspended = false

    /** Stop hidden live playback/DVR work. An active recording is the one
     * intentional exception: it owns the single provider stream while hidden. */
    fun suspendForBackground() {
        val p = player ?: return
        if (Recorder.activeName.value != null) {
            p.pause()
            return
        }
        if (liveMode && queue.isNotEmpty()) {
            backgroundSuspended = true
            playbackGen++
            stopGovernor()
            Timeshift.stop()
            p.stop()
        } else {
            p.pause()
        }
    }

    /** Reconnect the remembered live channel when the viewer returns. */
    fun resumeFromBackground() {
        val p = player ?: return
        if (backgroundSuspended && liveMode && queue.isNotEmpty()) {
            backgroundSuspended = false
            if (!simpleRaw) startGovernor()
            zapTo(currentIdxC.intValue)
        } else if (queue.isNotEmpty()) {
            p.play()
        }
    }

    fun livePathLabel(): String = when {
        simpleRaw -> "SMOOTH LIVE"
        directLive -> "DIRECT RESCUE"
        liveMode -> "DVR LIVE"
        else -> "ON DEMAND"
    }

    /** Provider-connection accounting for the user-selected 1/2/3 stream budget. */
    fun providerConnectionSlots(): Int {
        val cur = queue.getOrNull(currentIdxC.intValue) ?: return 0
        if (cur.url.startsWith("/") || cur.url.startsWith("file:")) return 0
        if (player == null || playStateC.intValue == Player.STATE_IDLE) return 0
        return if (liveMode && !simpleRaw && !directLive) {
            if (Timeshift.active) 1 else 0
        } else 1
    }

    /** Canonical provider URL for the currently playing live channel. */
    fun currentProviderUrl(): String? {
        val cur = queue.getOrNull(currentIdxC.intValue) ?: return null
        return if (cur.isLive) tsUrl(cur.url) else null
    }

    /** Switch the current LIVE channel between the light direct path and the
     * disk DVR path without rebuilding the Activity or losing Movies/Series. */
    fun setSmoothLive(enabled: Boolean, context: Context): Boolean {
        if (!liveMode || queue.isEmpty()) return false
        if (enabled && Recorder.activeName.value != null) return false
        prefsRef?.edit()?.putBoolean("simple_mode", enabled)?.apply()
        simpleRaw = enabled
        directLive = false
        if (enabled) {
            stopGovernor()
        } else {
            startGovernor()
        }
        zapTo(currentIdxC.intValue)
        return true
    }

    /** Full stop: close the one stream, stop the DVR recorder, free the decoders. */
    fun releaseAll() {
        playbackGen++
        stopGovernor()
        Timeshift.stop()
        TimeshiftServer.stop()
        runCatching { player?.release() }
        player = null
        queue = emptyList()
        liveMode = false
        directLive = false
        simpleRaw = false
        backgroundSuspended = false
        liveFails = 0
        playStateC.intValue = Player.STATE_IDLE
        streamDeadC.value = false
        everReadyC.value = false
        videoFpsC.floatValue = 0f
        standardMediaSources = null
        tolerantTsMediaSources = null
        browserVodMediaSources = null
        vodUaFallbackUsed = false
    }
}

/* ----------------------------- movies pane ----------------------------- */
@Composable
fun MoviesPane(
    prefs: SharedPreferences,
    data: AppData,
    selectedCat: String,
    onPlay: (Playable) -> Unit
) {
    val context = LocalContext.current

    if (data.movies.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No movies in this playlist", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("Your provider hasn't included any movies on this login.", fontSize = 13.sp, color = Muted)
        }
        return
    }

    val filtered = data.movies.filter { selectedCat == "all" || it.categoryId == selectedCat }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filtered) { m ->
            MediaRow(
                name = (if (WatchStore.isWatched(prefs, m.url)) "✓  " else "") + m.name,
                icon = m.icon,
                onClick = { onPlay(Playable(m.name, m.url, isLive = false, artwork = m.icon)) },
                trailing = { fm ->
                    IconButton(modifier = fm.then(Modifier.tvFocus(RoundedCornerShape(24.dp))), onClick = {
                        toast(context, DownloadStore.start(context, prefs, m.name, m.url))
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "Download for offline", tint = Muted)
                    }
                }
            )
        }
    }
}

/* ----------------------------- series pane ----------------------------- */
@Composable
fun SeriesPane(
    source: Source?,
    data: AppData,
    selectedCat: String,
    onSeries: (SeriesItem) -> Unit
) {
    if (source?.supportsSeries != true || data.series.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No series here", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                if (source?.supportsSeries == true)
                    "Your provider hasn't included any series on this login."
                else
                    "Series browsing works with a username & password (Xtream) playlist. M3U link playlists show their movies under Movies and everything else under Live.",
                fontSize = 13.sp, color = Muted
            )
        }
        return
    }

    val filtered = data.series.filter { selectedCat == "all" || it.categoryId == selectedCat }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filtered) { s ->
            MediaRow(name = s.name, icon = s.icon, onClick = { onSeries(s) })
        }
    }
}

/* ----------------------------- settings pane ----------------------------- */
@Composable
fun SettingsPane(prefs: SharedPreferences, onModeChanged: () -> Unit) {
    var bufferSec by remember { mutableIntStateOf(prefs.getInt("buffer_sec", 30)) }
    var autoLast by remember { mutableStateOf(prefs.getBoolean("autoplay_last", true)) }
    val ctx = LocalContext.current
    var usbPermissionRefresh by remember { mutableIntStateOf(0) }
    val usbPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        usbPermissionRefresh++
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp)
    ) {
        // ---- Simple Mode (lightweight live playback) ----
        Text("Smooth Live (Simple Mode)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        var simpleMode by remember { mutableStateOf(prefs.getBoolean("simple_mode", true)) }
        var showSimpleWarn by remember { mutableStateOf(false) }
        Text(
            "Recommended for everyday Fire Stick viewing. Smooth Live plays the provider directly and avoids continuous DVR disk writes. DVR Live adds pause, rewind and recording; a verified USB drive is strongly recommended for that mode. Movies, Series, Search, Downloads, and saved Recordings work in either mode.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("DVR Live", !simpleMode) {
                if (simpleMode) {
                    simpleMode = false
                    prefs.edit().putBoolean("simple_mode", false).apply()
                    Playback.releaseAll()
                    onModeChanged()
                    toast(
                        ctx,
                        if (Storage.usingDrive(ctx, prefs))
                            "DVR Live on — pause, rewind and recording use the verified USB drive."
                        else
                            "DVR Live on. For long DVR/recording use, connect and verify a USB drive so the Fire Stick's small internal storage is not doing continuous video writes."
                    )
                }
            }
            Chip("Smooth Live", simpleMode) {
                if (!simpleMode) showSimpleWarn = true
            }
        }
        if (showSimpleWarn) {
            AlertDialog(
                onDismissRequest = { showSimpleWarn = false },
                containerColor = SurfaceCol,
                title = { Text("Turn on Simple Mode?", color = Ink) },
                text = {
                    Text(
                        "Simple Mode changes LIVE TV only:\n\n" +
                            "\u2022 Live channels play directly from the provider with the lightest path.\n" +
                            "\u2022 Live DVR/timeshift, pause, rewind, recording, and the speed governor are off.\n" +
                            "\u2022 Movies, Series, Search, Downloads, and recordings you already saved stay available.\n" +
                            "\u2022 A live recording already running will stop, and scheduled live recordings do not start while Simple Mode is on.\n\n" +
                            "Use this when a provider channel has trouble. Turn it off anytime in Settings.",
                        color = Muted, fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSimpleWarn = false
                        simpleMode = true
                        prefs.edit().putBoolean("simple_mode", true).apply()
                        // Stop a LIVE recording because Simple Mode promises one raw
                        // playback stream. Downloads are local HTTP jobs and are not
                        // destroyed just because the live playback mode changed.
                        Recorder.stop(ctx)
                        Playback.releaseAll()
                        onModeChanged()
                        toast(ctx, "Simple Mode on — lightweight live playback.")
                    }) { Text("Turn it on", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showSimpleWarn = false }) { Text("Cancel", color = Muted) }
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        // ---- Provider connection budget ----
        Text("Provider streams", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        var providerStreams by remember { mutableIntStateOf(ProviderStreams.max(prefs)) }
        Text(
            "Set this to the number of simultaneous connections INCLUDED with your IPTV service — not the number of Fire TV tuners. EZTV defaults to 1. Recording the channel you are already watching in DVR Live shares that same stream; watching one channel while recording a different channel needs 2. A live stream + different-channel recording + download needs 3.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("1 stream", providerStreams == 1) {
                providerStreams = 1; ProviderStreams.setMax(prefs, 1)
            }
            Chip("2 streams", providerStreams == 2) {
                providerStreams = 2; ProviderStreams.setMax(prefs, 2)
            }
            Chip("3 streams", providerStreams == 3) {
                providerStreams = 3; ProviderStreams.setMax(prefs, 3)
            }
        }
        Text(
            "If your service only includes 1 stream, EZTV will warn/block combinations that need a second connection instead of letting the provider randomly kill one.",
            fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(20.dp))
        // ---- External drive storage ----
        Text("Storage", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        var storageRefresh by remember { mutableIntStateOf(0) }
        val drivePresent = remember(storageRefresh, usbPermissionRefresh) { Storage.drivePresent(ctx) }
        val driveRaw = remember(storageRefresh, usbPermissionRefresh) { Storage.removableDetected(ctx) && !drivePresent }
        var extOn by remember { mutableStateOf(Storage.isEnabled(prefs)) }
        val internalFree = remember(storageRefresh) { Storage.internalFreeBytes(ctx) }
        val driveFree = remember(storageRefresh) { Storage.driveFreeBytes(ctx) }
        Text(
            "Fire TV internal: ${if (internalFree >= 0) Storage.gb(internalFree) + " GB free" else "—"}" +
                if (drivePresent) "\nExternal drive: ${if (driveFree >= 0) Storage.gb(driveFree) + " GB free" else "detected"}"
                else "\nNo external drive detected.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                drivePresent ->
                    "Save downloads, recordings, and the live pause buffer to your plugged-in drive so the Fire Stick's small storage never fills up."
                driveRaw ->
                    "A USB drive is plugged in, but Fire OS has not exposed a path EZTV can prove writable. Use Recheck USB after granting the normal storage permission or reconnecting the drive. EZTV will never claim USB is active until a real write test passes."
                else ->
                    "Plug in a USB drive or SSD for saved downloads and recordings. Fire OS decides which portable volumes an app may write; EZTV tests the drive before offering it and falls back safely if the OS blocks it."
            },
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Fire Stick", !extOn) {
                extOn = false; Storage.setEnabled(prefs, false)
            }
            Chip("External drive", extOn && drivePresent) {
                if (!drivePresent) {
                    toast(ctx, "USB is not writable yet. Choose Recheck USB after reconnecting it or granting storage permission.")
                } else {
                    extOn = true; Storage.setEnabled(prefs, true)
                    toast(ctx, "Verified: new downloads and recordings will be written directly to the external drive.")
                }
            }
            if (android.os.Build.VERSION.SDK_INT <= 28 &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Chip("Allow USB", false) {
                    usbPermissionLauncher.launch(
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    )
                }
            }
            Chip("Recheck USB", false) {
                storageRefresh++
                val ok = Storage.drivePresent(ctx)
                toast(ctx, if (ok) "USB write test passed." else "USB still isn't writable by EZTV on this Fire OS setup.")
            }
        }
        Text(
            "New downloads and recordings are written directly to the selected destination — they do not move later. Anything already saved stays where it is. DVR Live uses an append-only temporary buffer up to ~1 GB on Fire Stick storage or ~3.5 GB on a verified USB drive, then continues live directly if the cap is reached. Smooth Live writes no temporary DVR file.",
            fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(20.dp))
        Text("Keep downloads", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Choose how long completed downloads stay. ‘Until I delete it’ has no automatic expiration.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        var keepDays by remember { mutableIntStateOf(DownloadStore.retentionDays(prefs)) }
        fun setKeep(days: Int) { keepDays = days; DownloadStore.setRetentionDays(prefs, days) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Until I delete it", keepDays == 0) { setKeep(0) }
                Chip("7 days", keepDays == 7) { setKeep(7) }
                Chip("14 days", keepDays == 14) { setKeep(14) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("30 days", keepDays == 30) { setKeep(30) }
                Chip("60 days", keepDays == 60) { setKeep(60) }
                Chip("90 days", keepDays == 90) { setKeep(90) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Start on last channel", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Like a cable box: opening the app tunes straight to whatever channel you were last watching.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("On", autoLast) { autoLast = true; prefs.edit().putBoolean("autoplay_last", true).apply() }
            Chip("Off", !autoLast) { autoLast = false; prefs.edit().putBoolean("autoplay_last", false).apply() }
        }

        Spacer(Modifier.height(20.dp))
        Text("Stream buffer", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "The app stores video ahead of what you're watching so a shaky connection doesn't cause stutter. Bigger = smoother on bad internet. Recommended: 30 seconds.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Small (10s)", bufferSec == 10) { bufferSec = 10; prefs.edit().putInt("buffer_sec", 10).apply() }
            Chip("Normal (30s)", bufferSec == 30) { bufferSec = 30; prefs.edit().putInt("buffer_sec", 30).apply() }
            Chip("Big (60s)", bufferSec == 60) { bufferSec = 60; prefs.edit().putInt("buffer_sec", 60).apply() }
        }

        Spacer(Modifier.height(20.dp))
        Text("Channel lock-in cushion", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "How much video EZTV collects before showing a live channel. Bigger cushion = steadier picture on weak channels, but changing channels takes longer. If certain channels keep re-buffering, bump this up.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        var lockSec by remember { mutableIntStateOf(prefs.getInt("live_start_ms", 4000) / 1000) }
        fun setLock(sec: Int) {
            lockSec = sec
            prefs.edit().putInt("live_start_ms", sec * 1000).apply()
            Playback.releaseAll()   // applies to the very next channel you play
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Fast (3s)", lockSec == 3) { setLock(3) }
            Chip("Normal (4s)", lockSec == 4) { setLock(4) }
            Chip("Steady (6s)", lockSec == 6) { setLock(6) }
            Chip("Max (10s)", lockSec == 10) { setLock(10) }
        }

        // ---- Live recovery behavior ----
        Text("After a live hiccup", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        var steadyRecovery by remember { mutableStateOf(prefs.getBoolean("live_steady_recovery", false)) }
        Text(
            "Natural is lightest: buffer when tuning, then play at normal 1.0× and resume normally after a brief stall. Steady adds the gentle cushion refill + stall recovery for especially bursty channels.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Natural (recommended)", !steadyRecovery) {
                steadyRecovery = false
                prefs.edit().putBoolean("live_steady_recovery", false).apply()
                Playback.releaseAll(); onModeChanged()
            }
            Chip("Steady weak-channel", steadyRecovery) {
                steadyRecovery = true
                prefs.edit().putBoolean("live_steady_recovery", true).apply()
                Playback.releaseAll(); onModeChanged()
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Auto frame rate (AFR)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Matches the Fire TV display to 24/25/30/50/60 fps content when the TV supports it. EZTV waits until playback is stable and restores normal display preference when you leave the player. The TV may briefly go black while HDMI changes rate.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        val legacyFps = prefs.getBoolean("match_fps", false)
        var matchFpsLive by remember { mutableStateOf(prefs.getBoolean("match_fps_live", legacyFps)) }
        var matchFpsVod by remember { mutableStateOf(prefs.getBoolean("match_fps_vod", legacyFps)) }
        Text("Live TV", fontSize = 11.sp, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Off", !matchFpsLive) { matchFpsLive = false; prefs.edit().putBoolean("match_fps_live", false).apply() }
            Chip("On", matchFpsLive) { matchFpsLive = true; prefs.edit().putBoolean("match_fps_live", true).apply() }
        }
        Spacer(Modifier.height(8.dp))
        Text("Movies & series", fontSize = 11.sp, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Off", !matchFpsVod) { matchFpsVod = false; prefs.edit().putBoolean("match_fps_vod", false).apply() }
            Chip("On", matchFpsVod) { matchFpsVod = true; prefs.edit().putBoolean("match_fps_vod", true).apply() }
        }

        Spacer(Modifier.height(20.dp))
        Text("Closed captions", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Show embedded subtitles/closed captions when the channel, movie, or episode provides a text track. You can also toggle CC from the live mini guide.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        var ccEnabledSetting by remember { mutableStateOf(prefs.getBoolean("cc_enabled", false)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Off", !ccEnabledSetting) {
                ccEnabledSetting = false
                prefs.edit().putBoolean("cc_enabled", false).apply()
                applyCaptionPreference(Playback.player, false)
            }
            Chip("On", ccEnabledSetting) {
                ccEnabledSetting = true
                prefs.edit().putBoolean("cc_enabled", true).apply()
                applyCaptionPreference(Playback.player, true)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Clock while watching", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Shows the time in the upper right corner during a show, like a cable box.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        var showClock by remember { mutableStateOf(prefs.getBoolean("show_clock", false)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Off", !showClock) { showClock = false; prefs.edit().putBoolean("show_clock", false).apply() }
            Chip("On", showClock) { showClock = true; prefs.edit().putBoolean("show_clock", true).apply() }
        }

        Spacer(Modifier.height(24.dp))
        Text("EZTV 4.18 — plays the playlists you provide. This app includes no channels or content of its own.", fontSize = 11.sp, color = Muted)
    }
}

/* ----------------------------- search (bottom tab, with recents) ----------------------------- */

private fun loadRecents(prefs: SharedPreferences): List<String> {
    val raw = prefs.getString("recent_searches", null) ?: return emptyList()
    return try {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun addRecent(prefs: SharedPreferences, q: String) {
    val query = q.trim()
    if (query.length < 2) return
    val next = (listOf(query) + loadRecents(prefs).filterNot { it.equals(query, ignoreCase = true) }).take(20)
    val arr = org.json.JSONArray()
    next.forEach { arr.put(it) }
    prefs.edit().putString("recent_searches", arr.toString()).apply()
}

@Composable
fun SearchTab(
    prefs: SharedPreferences,
    data: AppData,
    query: String,
    onQuery: (String) -> Unit,
    onPlay: (Playable) -> Unit,
    onSeries: (SeriesItem) -> Unit,
    onDemandWarning: String? = null
) {
    var recents by remember { mutableStateOf(loadRecents(prefs)) }

    fun saveRecent(q: String) {
        addRecent(prefs, q)
        recents = loadRecents(prefs)
    }

    Column(Modifier.fillMaxSize()) {
        // Voice search: OK on the mic opens Android's speech recognizer; what you
        // say fills the search box. (The remote's hardware mic button is locked
        // by Fire OS for Alexa, so this on-screen mic is the way to talk-search.)
        val speechLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) onQuery(spoken)
        }
        val ctx0 = LocalContext.current
        val voiceAvailable = remember(ctx0) {
            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .resolveActivity(ctx0.packageManager) != null
        }
        fun startVoice() {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say a show, movie, or channel")
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                toast(ctx0, "Voice search needs a speech app installed on this device.")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvTextField(
                value = query, onValueChange = onQuery,
                label = "Search",
                placeholder = "Search live, movies & series…",
                modifier = Modifier.weight(1f)
            )
            if (voiceAvailable) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .tvFocus(RoundedCornerShape(24.dp))
                        .background(Accent.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                        .clickable { startVoice() }
                        .padding(12.dp)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = Accent)
                }
            }
        }
        Text(
            "Matches any part of a name — \"wars\" finds Star Wars.",
            fontSize = 11.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (!onDemandWarning.isNullOrBlank()) {
            Text(
                "Movies/Series are still loading or unavailable; Live TV search still works. $onDemandWarning",
                fontSize = 10.sp, color = Live,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        val q = query.trim()
        if (q.length < 2) {
            if (recents.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Type at least 2 letters to search.", color = Muted, fontSize = 14.sp)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent searches", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Accent, modifier = Modifier.weight(1f))
                    Text(
                        "Clear",
                        fontSize = 12.sp, color = Muted,
                        modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)).clickable {
                            prefs.edit().remove("recent_searches").apply()
                            recents = emptyList()
                        }.padding(6.dp)
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(recents) { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocus()
                                .background(SurfaceCol, RoundedCornerShape(12.dp))
                                .clickable { onQuery(r) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = Muted)
                            Spacer(Modifier.width(10.dp))
                            Text(r, color = Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            return
        }

        val liveHits = data.live.filter { it.name.contains(q, ignoreCase = true) }.take(30)
        val movieHits = data.movies.filter { it.name.contains(q, ignoreCase = true) }.take(30)
        val seriesHits = data.series.filter { it.name.contains(q, ignoreCase = true) }.take(30)
        val guideHits = if (EpgStore.loaded.value) EpgStore.search(q, 30) else emptyList()

        // Match guide channels back to playable channels (by guide id, then by name).
        val context = LocalContext.current
        val byEpgId = remember(data) { data.live.filter { it.epgId != null }.associateBy { it.epgId!!.lowercase() } }
        val byNorm = remember(data) {
            data.live.associateBy { it.name.lowercase().replace(Regex("[^a-z0-9]"), "") }
        }
        val guideFmt = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }

        if (liveHits.isEmpty() && movieHits.isEmpty() && seriesHits.isEmpty() && guideHits.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nothing found for \"$q\".", color = Muted, fontSize = 14.sp)
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (liveHits.isNotEmpty()) {
                item { SectionHeader("Live TV") }
                items(liveHits) { ch ->
                    MediaRow(ch.name, ch.icon, onClick = { saveRecent(q); onPlay(livePlayable(prefs, ch)) })
                }
            }
            if (movieHits.isNotEmpty()) {
                item { SectionHeader("Movies") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(movieHits) { m ->
                            PosterCard(m.name, m.icon) {
                                saveRecent(q)
                                onPlay(Playable(m.name, m.url, isLive = false, artwork = m.icon))
                            }
                        }
                    }
                }
            }
            if (seriesHits.isNotEmpty()) {
                item { SectionHeader("Series") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(seriesHits) { s ->
                            PosterCard(s.name, s.icon) { saveRecent(q); onSeries(s) }
                        }
                    }
                }
            }
            if (guideHits.isNotEmpty()) {
                item { SectionHeader("TV Guide — upcoming shows") }
                items(guideHits) { hit ->
                    val ch = byEpgId[hit.channelXmlId]
                        ?: byNorm[hit.channelName.lowercase().replace(Regex("[^a-z0-9]"), "")]
                    val now = System.currentTimeMillis()
                    val airingNow = now in hit.entry.startMs until hit.entry.endMs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus()
                            .background(SurfaceCol, RoundedCornerShape(14.dp))
                            .clickable(enabled = ch != null) {
                                if (ch == null) return@clickable
                                saveRecent(q)
                                if (airingNow) {
                                    onPlay(livePlayable(prefs, ch))
                                } else {
                                    toast(context, ScheduleStore.add(context, prefs, hit.entry.title, ch.name, ch.url, hit.entry.startMs, hit.entry.endMs))
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.entry.title,
                                color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${guideFmt.format(Date(hit.entry.startMs))}  •  ${ch?.name ?: hit.channelName}" +
                                    if (ch == null) "  (channel not in your playlist)" else "",
                                color = if (airingNow) Accent else Muted, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (ch != null && (airingNow || ch.url.endsWith(".ts"))) {
                            Icon(
                                if (airingNow) Icons.Filled.PlayArrow else Icons.Filled.FiberManualRecord,
                                contentDescription = if (airingNow) "Watch now" else "Schedule recording",
                                tint = if (airingNow) Accent else Live
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Accent,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

/* ----------------------------- series detail ----------------------------- */
@Composable
fun SeriesDetailScreen(
    source: Source,
    s: SeriesItem,
    prefs: SharedPreferences,
    onPlayQueue: (List<Playable>, Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var eps by remember { mutableStateOf<Map<Int, List<Episode>>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var watchTick by remember { mutableIntStateOf(0) }
    BackHandler { onBack() }

    LaunchedEffect(s.id, err) {
        if (err == null && eps == null) {
            try {
                eps = source.seriesEpisodes(s.id)
            } catch (e: Exception) {
                err = e.message ?: "error"
            }
        }
    }

    // Every episode in order (season 1 ep 1 → last), so playback rolls forward automatically.
    val queue: List<Playable> = remember(eps) {
        eps?.flatMap { (season, list) ->
            list.map { ep ->
                Playable("${s.name} S${season}E${ep.episodeNum}", ep.url, isLive = false)
            }
        } ?: emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Muted)
            }
            Text(
                s.name,
                fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (eps != null && eps!!.isNotEmpty()) {
                TextButton(
                    modifier = Modifier.tvFocus(RoundedCornerShape(20.dp)),
                    onClick = {
                        val urls = eps!!.values.flatten().map { it.url }
                        WatchStore.clearAll(prefs, urls)
                        watchTick++
                        toast(context, "Watched history cleared for ${s.name}.")
                    }
                ) { Text("Reset watched", color = Muted, fontSize = 12.sp) }
            }
        }
        when {
            err != null -> ErrorBox(err!!, onRetry = { err = null }, title = "Couldn't load episodes")
            eps == null -> LoadingBox("Loading episodes…")
            eps!!.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No episodes listed for this series.", color = Muted, fontSize = 14.sp)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val wt = watchTick   // reading this refreshes labels after a reset
                eps!!.forEach { (season, list) ->
                    item { SectionHeader("Season $season") }
                    items(list) { ep ->
                        val w = WatchStore.get(prefs, ep.url)
                        val mark = when {
                            w?.watched == true -> "✓  "
                            (w?.pos ?: 0L) > 30_000 -> "▶  "
                            else -> ""
                        }
                        val label = "${mark}E${ep.episodeNum}  ${ep.title}"
                        val epName = "${s.name} S${season}E${ep.episodeNum}"
                        MediaRow(
                            name = label,
                            icon = null,
                            onClick = {
                                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                                onPlayQueue(queue, idx)
                            },
                            trailing = { fm ->
                                IconButton(modifier = fm.then(Modifier.tvFocus(RoundedCornerShape(24.dp))), onClick = {
                                    toast(
                                        context,
                                        DownloadStore.start(context, prefs, epName, ep.url)
                                    )
                                }) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download for offline", tint = Muted)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------- downloads ----------------------------- */
@Composable
fun DownloadsPane(prefs: SharedPreferences, onPlay: (Playable) -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(DownloadStore.load(prefs)) }
    // id -> (bytes so far, total bytes). Refreshed every second while anything is downloading.
    var progressMap by remember { mutableStateOf<Map<Long, Pair<Long, Long>>>(emptyMap()) }
    // id -> estimated seconds remaining (smoothed), for the "time left" readout.
    var etaMap by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    val lastBytes = remember { HashMap<Long, Long>() }
    val lastRate = remember { HashMap<Long, Double>() }

    LaunchedEffect(Unit) {
        while (true) {
            val inFlight = items.filter { d -> DownloadStore.isInFlight(context, d.id) }
            if (inFlight.isNotEmpty()) {
                val m = HashMap<Long, Pair<Long, Long>>()
                val eta = HashMap<Long, Long>()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    inFlight.forEach { d ->
                        DownloadStore.progress(context, d.id)?.let { m[d.id] = it }
                    }
                }
                // Compute smoothed download speed and seconds remaining.
                m.forEach { (id, p) ->
                    val done = p.first; val total = p.second
                    val prev = lastBytes[id]
                    if (prev != null && done > prev) {
                        val inst = (done - prev).toDouble()   // bytes per ~1s poll
                        val smooth = lastRate[id]?.let { it * 0.6 + inst * 0.4 } ?: inst
                        lastRate[id] = smooth
                        if (total > 0 && smooth > 1) {
                            eta[id] = ((total - done) / smooth).toLong().coerceAtLeast(0)
                        }
                    }
                    lastBytes[id] = done
                }
                progressMap = m
                etaMap = eta
                items = DownloadStore.load(prefs)   // pick up ones that just finished
            }
            kotlinx.coroutines.delay(1_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        val free = remember(items) { DownloadStore.freeBytes(context, prefs) }
        val onDrive = remember { Storage.usingDrive(context, prefs) }
        if (free >= 0) {
            Text(
                (if (onDrive) "External drive: " else "Fire Stick storage: ") +
                    "${String.format(java.util.Locale.US, "%.1f", free / 1_073_741_824.0)} GB free" +
                    if (free < 3_000_000_000L) "  •  Too low to start new downloads — free up 3 GB" else "",
                fontSize = 12.sp,
                color = if (free < 3_000_000_000L) Live else Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        Text(
            if (DownloadStore.retentionDays(prefs) <= 0)
                "Saved for offline watching. Downloads stay until you delete them. Downloads keep going even if you close the app."
            else
                "Saved for offline watching. Downloads are kept for ${DownloadStore.retentionDays(prefs)} days. Downloads keep going even if you close the app.",
            fontSize = 12.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nothing downloaded yet.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("Tap the ⬇ icon next to any movie or episode.", color = Muted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { d ->
                    val f = File(d.path)
                    val ready = DownloadStore.isReady(context, d)
                    val daysLeft = if (d.expires == Long.MAX_VALUE) Long.MAX_VALUE
                        else ((d.expires - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                    val prog = progressMap[d.id]
                    val btnFocus = remember { FocusRequester() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties { right = btnFocus }
                            .tvFocus()
                            .background(SurfaceCol, RoundedCornerShape(14.dp))
                            .clickable(enabled = ready) { onPlay(Playable(d.title, d.path, isLive = false)) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (ready) Icons.Filled.PlayArrow else Icons.Filled.Download,
                            contentDescription = null,
                            tint = if (ready) Accent else Muted
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                d.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (ready) {
                                Text(
                                    if (daysLeft == Long.MAX_VALUE) "Kept until you delete it"
                                    else "$daysLeft day${if (daysLeft == 1L) "" else "s"} left",
                                    color = Muted, fontSize = 12.sp
                                )
                            } else {
                                val done = prog?.first ?: 0L
                                val total = prog?.second ?: -1L
                                val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else null
                                val eta = etaMap[d.id]
                                val etaText = eta?.let { s ->
                                    when {
                                        s >= 3600 -> " • ${s / 3600}h ${(s % 3600) / 60}m left"
                                        s >= 60 -> " • ${s / 60}m ${s % 60}s left"
                                        else -> " • ${s}s left"
                                    }
                                } ?: ""
                                Text(
                                    when {
                                        pct != null -> "Downloading… $pct%  (${done / 1_048_576} MB of ${total / 1_048_576} MB)$etaText"
                                        done > 0 -> "Downloading… ${done / 1_048_576} MB so far"
                                        else -> "Starting download…"
                                    },
                                    color = Muted, fontSize = 12.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                if (pct != null) {
                                    LinearProgressIndicator(
                                        progress = { pct / 100f },
                                        color = Accent, trackColor = Surface2,
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = Accent, trackColor = Surface2,
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                        IconButton(
                            modifier = Modifier.focusRequester(btnFocus).tvFocus(RoundedCornerShape(24.dp)),
                            onClick = {
                                DownloadStore.stopAndRemove(context, prefs, d)
                                items = DownloadStore.load(prefs)
                            }
                        ) {
                            Icon(
                                if (ready) Icons.Filled.Delete else Icons.Filled.Stop,
                                contentDescription = if (ready) "Delete" else "Stop download",
                                tint = Muted
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ----------------------------- recordings ----------------------------- */
@Composable
fun RecordingsPane(prefs: SharedPreferences, onPlay: (Playable) -> Unit) {
    val context = LocalContext.current
    var files by remember {
        mutableStateOf(
            Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        )
    }
    var scheds by remember { mutableStateOf(ScheduleStore.load(prefs)) }
    val schedFmt = remember { SimpleDateFormat("EEE, MMM d  h:mm a", Locale.getDefault()) }
    val activeRecording = Recorder.activeName.value
    LaunchedEffect(activeRecording) {
        while (Recorder.activeName.value != null) {
            kotlinx.coroutines.delay(2_000)
            files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (ProviderStreams.max(prefs) == 1)
                "Provider streams: 1 • Recording the channel you are watching in DVR Live shares that stream. Recording a different channel takes over Live TV."
            else
                "Provider streams: ${ProviderStreams.max(prefs)} • EZTV may keep Live TV playing while a different channel records, up to your selected IPTV-plan limit.",
            color = Muted, fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Recorder.lastStatus.value?.let { status ->
            Text(
                status,
                color = if (status.contains("failed", true) || status.contains("did not", true)) Live else Accent,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        val free = remember(files) { DownloadStore.freeBytes(context, prefs) }
        val onDrive = remember { Storage.usingDrive(context, prefs) }
        if (free >= 0) {
            Text(
                (if (onDrive) "External drive: " else "Fire Stick storage: ") +
                    "${String.format(java.util.Locale.US, "%.1f", free / 1_073_741_824.0)} GB free (shared by recordings & downloads)" +
                    if (free < 2_500_000_000L) "  •  Too low to record safely" else "",
                fontSize = 12.sp,
                color = if (free < 2_500_000_000L) Live else Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        if (scheds.isNotEmpty()) {
            Text(
                "Scheduled",
                fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Accent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            scheds.forEach { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                        .background(SurfaceCol, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${schedFmt.format(Date(s.startMs))}  •  ${s.channelName}",
                            color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                        ScheduleStore.cancel(context, prefs, s.id)
                        scheds = ScheduleStore.load(prefs)
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Cancel", tint = Muted)
                    }
                }
            }
            Text(
                "The device must be powered on when a scheduled recording starts.",
                fontSize = 11.sp, color = Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        val active = Recorder.activeName.value
        if (active != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(SurfaceCol, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Live)
                Spacer(Modifier.width(8.dp))
                Text("Recording: $active", color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Button(onClick = {
                    Recorder.stop(context)
                    files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                }) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
        if (files.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No recordings yet.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("While watching live TV, tap the red ● record button.", color = Muted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { f ->
                    val sizeLabel = when {
                        f.length() < 1024 * 1024 -> "${(f.length() / 1024).coerceAtLeast(0)} KB"
                        f.length() < 10L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", f.length() / 1_048_576.0)
                        else -> "${f.length() / (1024 * 1024)} MB"
                    }
                    val trashFocus = remember { FocusRequester() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties { right = trashFocus }
                            .tvFocus()
                            .background(SurfaceCol, RoundedCornerShape(14.dp))
                            .clickable { onPlay(Playable(f.nameWithoutExtension, f.absolutePath, isLive = false)) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.nameWithoutExtension.removePrefix("REC_").replace('_', ' '),
                                color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(sizeLabel, color = Muted, fontSize = 12.sp)
                        }
                        IconButton(
                            modifier = Modifier.focusRequester(trashFocus).tvFocus(RoundedCornerShape(24.dp)),
                            onClick = {
                                f.delete()
                                files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                            }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

/* ----------------------------- playlists ----------------------------- */
@Composable
fun PlaylistsPane(
    playlists: List<Playlist>,
    activeIdx: Int,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Tap a playlist to switch to it. You can save up to ${PlaylistStore.MAX}.",
            fontSize = 12.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(playlists.size) { i ->
                val p = playlists[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocus()
                        .background(SurfaceCol, RoundedCornerShape(14.dp))
                        .clickable { onSelect(i) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(12.dp).background(
                            if (i == activeIdx) Accent else Line, CircleShape
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (p.type == "m3u") "Playlist link (M3U)" else "Username & password (Xtream)",
                            color = Muted, fontSize = 12.sp
                        )
                    }
                    if (i == activeIdx) {
                        Text("Active", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = { onDelete(i) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Muted)
                    }
                }
            }
            if (playlists.size < PlaylistStore.MAX) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus()
                            .background(Surface2, RoundedCornerShape(14.dp))
                            .clickable { onAdd() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Add a playlist", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/* ----------------------------- shared rows ----------------------------- */
@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .tvFocus(RoundedCornerShape(999.dp))
            .background(if (active) Accent else SurfaceCol, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (active) Color(0xFF20160A) else Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/* On Fire TV, a normal text field grabs the keyboard the instant you arrow
 * onto it — jarring, and it fights the remote. This wrapper shows the field as
 * a focusable BUTTON; the editable field + keyboard appear only after you press
 * OK on it. Press Back to leave edit mode. Used for every text input. */
@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var editing by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    if (editing) {
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        BackHandler(enabled = true) { editing = false }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = modifier.focusRequester(fr)
        )
    } else {
        Column(
            modifier
                .tvFocus(RoundedCornerShape(8.dp))
                .background(Color(0x33202634), RoundedCornerShape(8.dp))
                .clickable { editing = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(
                when {
                    value.isEmpty() -> placeholder.ifEmpty { "Press OK to type" }
                    password -> "\u2022".repeat(value.length.coerceAtMost(12))
                    else -> value
                },
                color = if (value.isEmpty()) Muted else Ink,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChannelIcon(name: String, icon: String?, size: androidx.compose.ui.unit.Dp = 46.dp) {
    Box(
        modifier = Modifier.size(size).background(Surface2, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        } else {
            Text(name.take(1).uppercase(), color = Muted, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PosterCard(name: String, icon: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .width(126.dp)
            .tvFocus(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            Modifier
                .width(126.dp)
                .height(184.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
        ) {
            if (!icon.isNullOrBlank()) {
                AsyncImage(
                    model = icon, contentDescription = name, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = Accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(name, color = Ink, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MediaRow(
    name: String,
    icon: String?,
    onClick: () -> Unit,
    trailing: (@Composable (Modifier) -> Unit)? = null
) {
    // Arrow RIGHT from the row lands directly on the trailing button
    // (download / delete) — no long-press gymnastics needed.
    val trailingFocus = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { if (trailing != null) right = trailingFocus }
            .tvFocus()
            .background(SurfaceCol, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelIcon(name, icon)
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            modifier = Modifier.weight(1f),
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) trailing(Modifier.focusRequester(trailingFocus))
    }
}

/* ----------------------------- player ----------------------------- */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    queue: List<Playable>,
    start: Int,
    startAtMs: Long? = null,
    attach: Boolean = false,
    source: Source?,
    prefs: SharedPreferences,
    onOpenSettings: (index: Int, posMs: Long) -> Unit,
    onBack: (index: Int, posMs: Long) -> Unit
) {
    // Safety: never crash on an empty queue — just go back.
    if (queue.isEmpty()) {
        LaunchedEffect(Unit) { onBack(0, 0L) }
        return
    }
    val context = LocalContext.current
    var resizeMode by remember {
        mutableIntStateOf(prefs.getInt("resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT))
    }
    // "Full screen" mode: an extra 34% blow-up on top of Stretch — beats even
    // black bars that are baked INTO the channel's picture. Old-school
    // customers want edge-to-edge, and this delivers it on any channel.
    var superStretch by remember { mutableStateOf(prefs.getBoolean("resize_super", false)) }
    // Playback state lives in the shared one-stream engine.
    val currentIdx by Playback.currentIdxC
    val current = queue[currentIdx.coerceIn(0, queue.size - 1)]
    var nowNext by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    // Our top bar shows and hides in lockstep with the player's own controls.
    var overlayVisible by remember { mutableStateOf(true) }
    var showRecordChoice by remember { mutableStateOf(false) }
    // Resume support: if there's a saved spot for the starting item, hold playback
    // and ask Resume / Start over. (Skipped when re-attaching to the running
    // stream from the corner — nothing should interrupt it.)
    val startItem = queue[start.coerceIn(0, queue.size - 1)]
    val resumeAt = remember {
        if (startItem.isLive || startAtMs != null || attach) null
        else WatchStore.get(prefs, startItem.url)?.let { w ->
            if (w.pos > 30_000 && (w.dur <= 0 || w.pos < (w.dur * 0.95).toLong())) w.pos else null
        }
    }
    var pendingResume by remember { mutableStateOf(resumeAt) }
    val streamDead = Playback.streamDeadC
    val playState = Playback.playStateC
    val everReady = Playback.everReadyC
    var pvRef by remember { mutableStateOf<PlayerView?>(null) }

    // ---- X1-style mini guide ----
    // Press OK on a live channel → a slim strip along the bottom shows the
    // last few channels you watched plus the next few in the lineup. Your show
    // keeps playing full-screen the whole time. Arrow to highlight, OK to tune,
    // OK again (or 4s idle) to tuck it away.
    var miniGuideOpen by remember { mutableStateOf(false) }
    var ccEnabled by remember { mutableStateOf(prefs.getBoolean("cc_enabled", false)) }
    val titleFocus = remember { FocusRequester() }
    // Whenever the menus appear, park the remote on the show title —
    // OK there closes the menus, arrows reach the other buttons.
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            kotlinx.coroutines.delay(150)
            runCatching { titleFocus.requestFocus() }
        }
    }
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // THE one player. attach=true means the stream is already running (it was
    // in the corner) — we just show it, nothing reloads or reconnects.
    // Optional cable-box clock in the corner while watching.
    val showClock = remember { prefs.getBoolean("show_clock", false) }
    // Frame-rate matching (Settings, default off): once you've SETTLED on a
    // channel for a few seconds, ask the TV to switch to the video's native
    // rate. The delay means rapid zapping never thrashes the HDMI handshake.
    val legacyFps = prefs.getBoolean("match_fps", false)
    var matchFps by remember(current.isLive) {
        mutableStateOf(
            if (current.isLive) prefs.getBoolean("match_fps_live", legacyFps)
            else prefs.getBoolean("match_fps_vod", legacyFps)
        )
    }
    val detectedFps by Playback.videoFpsC
    LaunchedEffect(playState.intValue, currentIdx, matchFps, detectedFps) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        if (!matchFps) {
            clearFrameRateMatch(activity)
            return@LaunchedEffect
        }
        if (playState.intValue != Player.STATE_READY) return@LaunchedEffect
        kotlinx.coroutines.delay(if (current.isLive) 4_000L else 900L)
        if (playState.intValue != Player.STATE_READY) return@LaunchedEffect
        var fps = Playback.player?.videoFormat?.frameRate ?: detectedFps
        // Some Fire/codec combinations populate frameRate slightly after READY.
        repeat(8) {
            if (fps > 0f) return@repeat
            kotlinx.coroutines.delay(350)
            fps = Playback.player?.videoFormat?.frameRate ?: Playback.videoFpsC.floatValue
        }
        if (fps > 0f) applyFrameRateMatch(activity, fps)
    }
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(showClock) {
        if (showClock) {
            while (true) {
                clockText = fmt.format(Date())
                kotlinx.coroutines.delay(20_000)
            }
        }
    }
    val exo = remember {
        Playback.open(context, prefs, queue, start, startAtMs, attachOnly = attach).also { p ->
            if (!attach && resumeAt != null) p.playWhenReady = false
        }
    }
    fun setCcEnabled(on: Boolean) {
        ccEnabled = on
        prefs.edit().putBoolean("cc_enabled", on).apply()
        applyCaptionPreference(exo, on)
        toast(context, if (on) "Closed captions on when this program provides them." else "Closed captions off.")
    }
    DisposableEffect(Unit) {
        onDispose {
            // The stream KEEPS PLAYING (it moves to the corner). Just remember
            // where we are in movies/episodes, and detach this screen's view.
            runCatching {
                val i = exo.currentMediaItemIndex
                if (i in queue.indices && !queue[i].isLive && exo.currentPosition > 10_000) {
                    WatchStore.setProgress(
                        prefs, queue[i].url, exo.currentPosition,
                        if (exo.duration > 0) exo.duration else 0L
                    )
                }
            }
            pvRef?.player = null
            if (matchFps) (context as? android.app.Activity)?.let { clearFrameRateMatch(it) }
        }
    }
    // When a DVR recording finishes playing, offer to clean it up — keeps the
    // 16 GB Fire Stick healthy without anyone thinking about storage.
    var askDeleteRecording by remember { mutableStateOf(false) }
    LaunchedEffect(playState.intValue) {
        if (playState.intValue == Player.STATE_ENDED &&
            current.url.startsWith("/") && current.url.contains("/recordings/")
        ) {
            askDeleteRecording = true
        }
    }
    if (askDeleteRecording) {
        AlertDialog(
            onDismissRequest = { askDeleteRecording = false },
            containerColor = SurfaceCol,
            title = { Text("Finished watching", color = Ink) },
            text = {
                Text(
                    "Delete this recording to free up space? (${File(current.url).length() / (1024 * 1024)} MB)",
                    color = Muted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askDeleteRecording = false
                    runCatching { File(current.url).delete() }
                    toast(context, "Recording deleted.")
                    onBack(Playback.currentIdxC.intValue, 0L)
                }) { Text("Delete it", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askDeleteRecording = false
                    onBack(Playback.currentIdxC.intValue, 0L)
                }) { Text("Keep it", color = Muted) }
            }
        )
    }
    // Live percentage for the locking-in / buffering message — people will
    // wait when they can SEE progress, and a channel frozen at 0% tells them
    // it's off the air (game-day channels, etc.) without any guesswork.
    var bufPct by remember { mutableIntStateOf(0) }
    val lockTargetMs = remember { prefs.getInt("live_start_ms", 4_000).coerceIn(2_000, 12_000).toFloat() }
    LaunchedEffect(playState.intValue) {
        // Only spins while actually buffering; the effect re-runs (and this
        // loop exits) the moment the state changes to READY. 600ms is plenty
        // for a smooth-looking %, and keeps the poll off the hot path.
        if (playState.intValue == Player.STATE_BUFFERING) {
            bufPct = 0
            while (playState.intValue == Player.STATE_BUFFERING) {
                val targetMs = if (!everReady.value) lockTargetMs else lockTargetMs * 2f
                bufPct = ((exo.totalBufferedDuration / targetMs) * 100f).toInt().coerceIn(0, 99)
                kotlinx.coroutines.delay(600)
            }
        }
    }
    // ONE press of Back = out to the channel list (the show keeps playing in
    // the corner). Another press there = main menu. Simple, like a cable box.
    BackHandler {
        if (miniGuideOpen) {
            miniGuideOpen = false
        } else {
            onBack(Playback.currentIdxC.intValue, exo.currentPosition.coerceAtLeast(0L))
        }
    }

    // Channel surfing: +1 / −1 through the category, wrapping at the ends —
    // exactly like the channel up/down buttons on a cable remote. All channel
    // changes go through the DVR engine (one stream, one recorder).
    fun zapReady(): Boolean =
        queue.size > 1 && queue.getOrNull(Playback.currentIdxC.intValue)?.isLive == true
    fun zap(dir: Int) {
        if (Recorder.activeName.value != null && ProviderStreams.max(prefs) < 2) {
            toast(context, "That would need a second provider stream while recording. Your EZTV setting is 1 stream — stay on this channel or stop recording.")
            return
        }
        Playback.zapTo(Playback.currentIdxC.intValue + dir)
    }

    // The catch-all: presses nothing else handled. Media keys control playback
    // directly; channel & D-pad up/down zap channels; anything else brings the
    // menus back. Works every time.
    DisposableEffect(Unit) {
        // These must win over the video view's own key handling, so they're
        // checked before anything on screen sees the press.
        PlayerKeys.priority = { key ->
            when (key) {
                // Real channel buttons (many TV remotes have them): always zap.
                // Channel UP = next channel up the lineup (higher position);
                // DOWN = previous. (Was reversed.)
                android.view.KeyEvent.KEYCODE_CHANNEL_UP ->
                    if (zapReady()) { zap(+1); true } else false
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN ->
                    if (zapReady()) { zap(-1); true } else false
                // D-pad up = channel up (next); down = previous.
                android.view.KeyEvent.KEYCODE_DPAD_UP ->
                    if (zapReady() && !overlayVisible && !miniGuideOpen) { zap(+1); true } else false
                android.view.KeyEvent.KEYCODE_DPAD_DOWN ->
                    if (zapReady() && !overlayVisible && !miniGuideOpen) { zap(-1); true } else false
                else -> false
            }
        }
        PlayerKeys.handler = { key ->
            when (key) {
                // Fire TV's Menu button is a natural cable-box CC shortcut.
                // It only flips Media3 text-track selection; no extra decoder or
                // background worker is created.
                android.view.KeyEvent.KEYCODE_MENU -> {
                    setCcEnabled(!ccEnabled)
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (Playback.simpleRaw && current.isLive) true   // no pause in Simple Mode
                    else { exo.playWhenReady = !exo.playWhenReady; true }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { exo.playWhenReady = true; true }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (Playback.simpleRaw && current.isLive) true
                    else { exo.playWhenReady = false; true }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (current.isLive) {
                        if (!Playback.simpleRaw && !Playback.directLive && exo.isCurrentMediaItemSeekable) {
                            val edge = Timeshift.windowMs().coerceAtLeast(exo.currentPosition)
                            exo.seekTo((exo.currentPosition + 30_000L).coerceAtMost((edge - 500L).coerceAtLeast(0L)))
                        }
                    } else exo.seekForward()
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (current.isLive) {
                        if (!Playback.simpleRaw && !Playback.directLive && exo.isCurrentMediaItemSeekable) {
                            exo.seekTo((exo.currentPosition - 30_000L).coerceAtLeast(0L))
                        }
                    } else exo.seekBack()
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER -> {
                    when {
                        // When the recent-channel guide is open, Compose must
                        // receive OK so the focused card can tune the channel.
                        miniGuideOpen -> false
                        current.isLive -> {
                            // Live OK always means the cable-box mini guide. The
                            // stock PlayerView controller was stealing this press
                            // in v4.17, leaving only a one-channel popup.
                            pvRef?.hideController()
                            overlayVisible = false
                            miniGuideOpen = true
                            true
                        }
                        else -> { pvRef?.showController(); true }
                    }
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Let the recent-channel LazyRow own D-pad focus.
                    if (miniGuideOpen) false else { pvRef?.showController(); true }
                }
                else -> false
            }
        }
        onDispose {
            PlayerKeys.handler = null
            PlayerKeys.priority = null
        }
    }

    // Remember where they are, a few times a minute.
    LaunchedEffect(currentIdx) {
        val item = queue[currentIdx.coerceIn(0, queue.size - 1)]
        if (item.isLive) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5000)
            val pos = exo.currentPosition
            val dur = exo.duration
            if (pos > 10_000) {
                WatchStore.setProgress(prefs, item.url, pos, if (dur > 0) dur else 0L)
            }
        }
    }

    LaunchedEffect(current.epgId, EpgStore.loaded.value) {
        if (!current.isLive) return@LaunchedEffect
        val fromGuide = EpgStore.guide(current.guideKey, current.name)
        if (fromGuide.isNotEmpty()) {
            nowNext = fromGuide.take(2)
        } else {
            val id = current.epgId
            if (id != null && source != null && source.supportsEpg) {
                nowNext = source.epg(id, 2)
            }
        }
    }

    val recordingThis = Recorder.activeName.value.let { a ->
        a != null && (a == current.name || a.endsWith("(${current.name})"))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Phones & tablets: swipe up = previous channel in the list,
            // swipe down = next. Taps still work normally for the controls.
            .pointerInput(queue.size) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (zapReady()) {
                            when {
                                totalDrag < -120f -> zap(+1)   // swiped up = channel up
                                totalDrag > 120f -> zap(-1)    // swiped down = channel down
                            }
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    // Live TV has ONE controller: EZTV's cable-box mini guide.
                    // Media3's stock controller was competing for OK/focus and
                    // trapping the remote on its gear/title row. VOD/recordings
                    // still use Media3's normal controller.
                    useController = !current.isLive
                    // Hold the last good frame across prepare()/reconnect cycles
                    // instead of flashing black 3–4 times when a live channel
                    // starts. Also use a SurfaceView (cheaper on Fire TV) and a
                    // black shutter so intermediate states never flash through.
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    // Never let the screen saver / sleep kick in while watching.
                    keepScreenOn = true
                    // Grab the remote's key presses so OK re-opens the controls
                    // even after they've auto-hidden (Fire TV).
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    controllerShowTimeoutMs = 5000
                    setShowNextButton(queue.size > 1)
                    setShowPreviousButton(queue.size > 1)
                    // In direct-fallback live (no DVR file), there's nothing to
                    // scrub through — hide the rewind/FF buttons so pressing them
                    // can't misbehave. Normal DVR live and VOD keep them.
                    if ((Playback.directLive || Playback.simpleRaw) && current.isLive) {
                        setShowRewindButton(false)
                        setShowFastForwardButton(false)
                    }
                    // Hot-pink highlight on the play/pause/FF/RW/settings buttons
                    // (so you can tell where you are), and a colored "buffered
                    // ahead" section on the progress bar.
                    post { tintPlayerControls(this) }
                    // Show/hide our top bar together with the player's controls, and
                    // when they hide, pull remote focus back onto the video view so
                    // the next press is never lost.
                    val pv = this
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { vis ->
                            overlayVisible = !current.isLive && vis == android.view.View.VISIBLE
                            if (vis != android.view.View.VISIBLE) {
                                pv.post { pv.requestFocus() }
                            }
                        }
                    )
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
                pvRef = view
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    // FULL SCREEN mode: blow the picture up 34% past the edges —
                    // wipes out black bars even when they're part of the channel's
                    // own picture. Old-school edge-to-edge TV.
                    scaleX = if (superStretch) 1.34f else 1f,
                    scaleY = if (superStretch) 1.34f else 1f
                )
        )
        // Cable-box clock (Settings › Clock while watching).
        // ---- X1-style mini guide overlay (live only) ----
        if (miniGuideOpen && current.isLive) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                MiniGuide(
                    queue = queue,
                currentIdx = currentIdx,
                recent = RecentChannels.items.toList(),
                nowNext = nowNext,
                fmt = fmt,
                prefs = prefs,
                ccEnabled = ccEnabled,
                onToggleCc = { setCcEnabled(!ccEnabled) },
                afrEnabled = matchFps,
                onToggleAfr = {
                    matchFps = !matchFps
                    prefs.edit().putBoolean("match_fps_live", matchFps).apply()
                    if (!matchFps) (context as? android.app.Activity)?.let { clearFrameRateMatch(it) }
                },
                onToggleLiveMode = {
                    val nextSmooth = !Playback.simpleRaw
                    if (nextSmooth && Recorder.activeName.value != null) {
                        toast(context, "Stop recording before switching to Smooth Live.")
                    } else {
                        Playback.setSmoothLive(nextSmooth, context)
                    }
                    if (!nextSmooth && !Storage.usingDrive(context, prefs)) {
                        toast(context, "DVR Live is on. A verified USB drive is recommended for long pause/rewind/recording sessions.")
                    }
                    miniGuideOpen = false
                },
                onTune = { ch ->
                    if (Recorder.activeName.value != null && ch.url != current.url && ProviderStreams.max(prefs) < 2) {
                        toast(context, "Changing channels while recording needs 2 provider streams. Your EZTV setting is 1.")
                    } else {
                        miniGuideOpen = false
                        if (ch.url != current.url) Playback.zapToChannel(ch)
                    }
                },
                onOpenSettings = {
                    miniGuideOpen = false
                    onOpenSettings(Playback.currentIdxC.intValue, exo.currentPosition.coerceAtLeast(0L))
                },
                    onRetry = { Playback.tryAgain() },
                    onClose = { miniGuideOpen = false }
                )
            }
        }
        if (showClock && clockText.isNotEmpty()) {
            Text(
                clockText,
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 14.dp)
                    .background(Color(0x66000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        // Friendly status while the stream settles — so the customer knows the
        // wait is on purpose, not broken.
        if (!streamDead.value && playState.value == Player.STATE_BUFFERING) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .background(Color(0xB315181E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Accent, strokeWidth = 3.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$bufPct%",
                    color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (!everReady.value)
                        "Locking in ${current.name}… a few seconds gets you a clean, steady picture."
                    else
                        "Buffering ahead to keep your video smooth — hang tight…",
                    color = Ink, fontSize = 12.sp
                )
                if (!everReady.value) {
                    Text(
                        "Stuck at 0%? This channel may be off the air right now (some only broadcast during games or events).",
                        color = Muted, fontSize = 10.sp
                    )
                }
            }
        }
        // Channel gave up after every reconnect attempt — tell them plainly.
        if (streamDead.value) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC15181E), RoundedCornerShape(14.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("This channel isn't coming in", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "We tried several times. This is on your IPTV service's end — not your internet or this device. Try another channel, or ask your IPTV service about this one.",
                    color = Muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.tvFocus(RoundedCornerShape(22.dp)),
                    onClick = { Playback.tryAgain() }
                ) { Text("Try again") }
            }
        }
        if (overlayVisible) Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                    onBack(Playback.currentIdxC.intValue, exo.currentPosition.coerceAtLeast(0L))
                }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .focusRequester(titleFocus)
                        .tvFocus(RoundedCornerShape(8.dp))
                        .clickable {
                            pvRef?.hideController()
                            overlayVisible = false
                        }
                        .padding(4.dp)
                ) {
                    Text(
                        current.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (queue.size > 1) {
                        Text(
                            if (current.isLive)
                                "Channel ${currentIdx + 1} of ${queue.size} — ↑/↓ or CH buttons change channels"
                            else
                                "Episode ${currentIdx + 1} of ${queue.size} — next plays automatically",
                            color = Color(0xFFB9BDC7), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Screen shape: Fit (black bars) → Stretch (fill screen) → Zoom (crop edges)
                IconButton(
                    modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)),
                    onClick = {
                        // Cycle: Fit -> Stretch -> Zoom -> FULL SCREEN -> Fit
                        val label: String
                        if (superStretch) {
                            superStretch = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            label = "Fit — whole picture, may have black bars"
                        } else when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                label = "Stretch — fills the screen"
                            }
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                label = "Zoom — crops the edges"
                            }
                            else -> {
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                superStretch = true
                                label = "FULL SCREEN — edge to edge, even over built-in bars"
                            }
                        }
                        prefs.edit()
                            .putInt("resize_mode", resizeMode)
                            .putBoolean("resize_super", superStretch)
                            .apply()
                        toast(context, label)
                    }
                ) {
                    Icon(
                        Icons.Filled.AspectRatio,
                        contentDescription = "Screen shape: fit, stretch, or zoom",
                        tint = Color.White
                    )
                }
                if (current.canRecord && !Playback.simpleRaw) {
                    // While recording, a red REC badge sits right next to the
                    // button (which becomes a Stop button) — one press stops it.
                    if (recordingThis) {
                        Text(
                            "● REC",
                            color = Live, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0x66000000), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(
                        modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)),
                        onClick = {
                            if (recordingThis) {
                                Recorder.stop(context)
                                toast(context, "Recording saved — find it in Recordings.")
                            } else {
                                showRecordChoice = true
                            }
                        }
                    ) {
                        Icon(
                            if (recordingThis) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = if (recordingThis) "Stop recording" else "Record",
                            tint = Live
                        )
                    }
                }
            }
            if (nowNext.isNotEmpty()) {
                val now = nowNext.firstOrNull { System.currentTimeMillis() in it.startMs until it.endMs }
                    ?: nowNext.first()
                val next = nowNext.getOrNull(nowNext.indexOf(now) + 1)
                Text(
                    "Now: ${now.title}",
                    color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp)
                )
                if (next != null) {
                    Text(
                        "Next at ${fmt.format(Date(next.startMs))}: ${next.title}",
                        color = Color(0xFFB9BDC7), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }

        if (pendingResume != null) {
            val mins = (pendingResume!! / 60000).toInt()
            val secs = ((pendingResume!! % 60000) / 1000).toInt()
            AlertDialog(
                onDismissRequest = { },
                containerColor = SurfaceCol,
                title = { Text("Resume ${current.name}?", color = Ink) },
                text = {
                    Text(
                        "You left off at %d:%02d.".format(mins, secs),
                        color = Muted, fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val p = pendingResume!!
                        pendingResume = null
                        exo.seekTo(p)
                        exo.playWhenReady = true
                    }) { Text("Resume", color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingResume = null
                        WatchStore.clear(prefs, current.url)
                        exo.seekTo(0)
                        exo.playWhenReady = true
                    }) { Text("Start over", color = Muted) }
                }
            )
        }

        if (showRecordChoice) {
            val nowShow = nowNext.firstOrNull { System.currentTimeMillis() in it.startMs until it.endMs }
            AlertDialog(
                onDismissRequest = { showRecordChoice = false },
                containerColor = SurfaceCol,
                title = { Text("Record ${current.name}?", color = Ink) },
                text = {
                    Text(
                        if (nowShow != null)
                            "\"${nowShow.title}\" ends at ${fmt.format(Date(nowShow.endMs))}. Recording keeps going in the background even if you leave the app."
                        else
                            "Recording keeps going in the background even if you leave the app. Tap the red button again to stop.",
                        color = Muted, fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRecordChoice = false
                        val spaceMsg = Recorder.spaceCheck(context)
                        if (spaceMsg != null && !spaceMsg.startsWith("WARN:")) {
                            toast(context, spaceMsg)
                        } else {
                            if (spaceMsg != null) toast(context, spaceMsg.removePrefix("WARN:"))
                            // Recording the channel being WATCHED copies from the
                            // DVR file — no second provider connection, so the
                            // picture no longer pauses when you press record.
                            val tee = Playback.canTeeRecording() || Playback.prepareCurrentForRecording()
                            if (!tee) {
                                toast(context, "Recording needs DVR Live. Turn off Smooth Live, then try again.")
                            } else if (nowShow != null) {
                                Recorder.start(context, tsUrl(current.url), "${nowShow.title} (${current.name})", nowShow.endMs + 2 * 60 * 1000, teeFromTimeshift = tee)
                                toast(context, "Recording until this show ends.")
                            } else {
                                Recorder.start(context, tsUrl(current.url), current.name, teeFromTimeshift = tee)
                                toast(context, "Recording started. Tap the red button again to stop.")
                            }
                        }
                    }) {
                        Text(if (nowShow != null) "Record this show" else "Start recording", color = Accent)
                    }
                },
                dismissButton = {
                    if (nowShow != null) {
                        TextButton(onClick = {
                            showRecordChoice = false
                            val spaceMsg2 = Recorder.spaceCheck(context)
                            if (spaceMsg2 != null && !spaceMsg2.startsWith("WARN:")) {
                                toast(context, spaceMsg2)
                            } else {
                                if (spaceMsg2 != null) toast(context, spaceMsg2.removePrefix("WARN:"))
                                val tee = Playback.canTeeRecording() || Playback.prepareCurrentForRecording()
                                if (tee) {
                                    Recorder.start(context, tsUrl(current.url), current.name, teeFromTimeshift = true)
                                    toast(context, "Recording until you stop it.")
                                } else {
                                    toast(context, "Recording needs DVR Live. Turn off Smooth Live, then try again.")
                                }
                            }
                        }) {
                            Text("Record until I stop", color = Muted)
                        }
                    } else {
                        TextButton(onClick = { showRecordChoice = false }) {
                            Text("Cancel", color = Muted)
                        }
                    }
                }
            )
        }
    }
}

/* ---------------------------------------------------------------------------
 * X1-STYLE MINI GUIDE
 * A slim strip along the bottom of the live picture. Your show keeps playing
 * full-screen behind it. Left group = the last few channels you watched;
 * right group = the next few in the lineup. Arrow to highlight, OK to tune.
 * A row of buttons (Settings / Refresh) sits above the strip. Auto-hides
 * after ~5 seconds of no input.
 * ------------------------------------------------------------------------- */
@Composable
private fun MiniGuide(
    queue: List<Playable>,
    currentIdx: Int,
    recent: List<Playable>,
    nowNext: List<EpgEntry>,
    fmt: SimpleDateFormat,
    prefs: SharedPreferences,
    ccEnabled: Boolean,
    onToggleCc: () -> Unit,
    afrEnabled: Boolean,
    onToggleAfr: () -> Unit,
    onToggleLiveMode: () -> Unit,
    onTune: (Playable) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val player = Playback.player
    val current = queue.getOrNull(currentIdx)
    val nowMs = System.currentTimeMillis()
    val nowShow = nowNext.firstOrNull { nowMs in it.startMs until it.endMs } ?: nowNext.firstOrNull()
    val nextShow = nowShow?.let { ns -> nowNext.getOrNull(nowNext.indexOf(ns) + 1) }
        ?: nowNext.firstOrNull { it.startMs > nowMs }

    val entries = remember(recent, queue, currentIdx, current?.url) {
        val out = ArrayList<Playable>(7)
        val seen = HashSet<String>()
        current?.url?.let { seen.add(it) }
        recent.forEach { ch -> if (out.size < 7 && seen.add(ch.url)) out.add(ch) }
        if (queue.size > 1 && out.size < 7) {
            var step = 1
            while (out.size < 7 && step < queue.size) {
                val forward = queue[(currentIdx + step) % queue.size]
                if (seen.add(forward.url)) out.add(forward)
                if (out.size >= 7) break
                val back = queue[((currentIdx - step) % queue.size + queue.size) % queue.size]
                if (seen.add(back.url)) out.add(back)
                step++
            }
        }
        out
    }

    var playerBufferMs by remember { mutableLongStateOf(0L) }
    var playerPosMs by remember { mutableLongStateOf(0L) }
    var dvrWindowMs by remember { mutableLongStateOf(0L) }
    var dvrBytes by remember { mutableLongStateOf(0L) }
    var displayHz by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }
    var isSeekable by remember { mutableStateOf(player?.isCurrentMediaItemSeekable == true) }
    LaunchedEffect(Unit) {
        while (true) {
            playerBufferMs = player?.totalBufferedDuration ?: 0L
            playerPosMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            dvrWindowMs = Timeshift.windowMs()
            dvrBytes = Timeshift.bytesWritten
            isPlaying = player?.isPlaying == true
            isSeekable = player?.isCurrentMediaItemSeekable == true
            displayHz = runCatching {
                (context as? android.app.Activity)?.windowManager?.defaultDisplay?.mode?.refreshRate ?: 0f
            }.getOrDefault(0f)
            kotlinx.coroutines.delay(400)
        }
    }

    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastTouch, isPlaying) {
        // Keep controls visible while paused — that is when the viewer most
        // needs the DVR bar. While playing, 15 seconds gives a Fire TV user
        // enough time to D-pad across recents/settings without the guide vanishing.
        if (isPlaying) {
            kotlinx.coroutines.delay(15_000)
            if (isPlaying && System.currentTimeMillis() - lastTouch >= 15_000) onClose()
        }
    }

    val playFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val recentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { playFocus.requestFocus() }
    }

    fun touch() { lastTouch = System.currentTimeMillis() }
    fun seekDvr(deltaMs: Long) {
        touch()
        val p = player ?: return
        if (Playback.simpleRaw || Playback.directLive || current?.isLive != true) {
            toast(context, "Rewind needs DVR Live.")
            return
        }
        if (!isSeekable) {
            toast(context, "DVR seek is still initializing — give it a few seconds.")
            return
        }
        val liveEdge = dvrWindowMs.coerceAtLeast(p.currentPosition)
        val target = (p.currentPosition + deltaMs).coerceIn(0L, (liveEdge - 500L).coerceAtLeast(0L))
        runCatching {
            p.seekTo(target)
            p.playWhenReady = true
        }.onFailure { toast(context, "DVR seek is not ready yet — wait a few seconds and try again.") }
    }

    val lockMs = prefs.getInt("live_start_ms", 4_000).coerceIn(2_000, 12_000)
    val bufferGoalMs = (lockMs * 2L).coerceAtLeast(8_000L)
    val bufferProgress = (playerBufferMs.toFloat() / bufferGoalMs.toFloat()).coerceIn(0f, 1f)
    val sourceFps = Playback.videoFpsC.floatValue
    val dvrActive = current?.isLive == true && !Playback.simpleRaw && !Playback.directLive && Timeshift.file != null
    val dvrProgress = if (dvrWindowMs > 0L) (playerPosMs.toFloat() / dvrWindowMs.toFloat()).coerceIn(0f, 1f) else 1f
    val behindMs = (dvrWindowMs - playerPosMs).coerceAtLeast(0L)

    fun shortTime(ms: Long): String {
        val sec = (ms / 1000L).coerceAtLeast(0L)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) touch()
                false
            }
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0x22000000), Color(0xFA000000))
                )
            )
            .padding(top = 18.dp, bottom = 14.dp, start = 16.dp, end = 16.dp)
    ) {
        // Current / next — information only, so focus never gets trapped here.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (current != null) {
                ChannelIcon(current.name, current.artwork, 40.dp)
                Spacer(Modifier.width(9.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    current?.name ?: "",
                    color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (nowShow != null) {
                    Text(
                        "${nowShow.title}  •  ${fmt.format(Date(nowShow.startMs))}–${fmt.format(Date(nowShow.endMs))}",
                        color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (nextShow != null) {
                    Text(
                        "Next ${fmt.format(Date(nextShow.startMs))}: ${nextShow.title}",
                        color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MiniGuideChip("↻", Modifier.focusProperties { down = playFocus }) { touch(); onRetry() }
            Spacer(Modifier.width(6.dp))
            MiniGuideChip("⚙", Modifier.focusProperties { down = playFocus }) { touch(); onOpenSettings() }
        }

        Spacer(Modifier.height(8.dp))

        // PRIMARY REMOTE CONTROLS. Play/Pause gets focus every time OK opens the
        // guide, exactly as requested. Media3's stock controller is disabled on
        // live TV so there is no second menu fighting for D-pad focus.
        val downFromControls = if (dvrActive) timelineFocus else recentFocus
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniGuideControl("↶ 30", enabled = dvrActive && isSeekable, modifier = Modifier.focusProperties { down = downFromControls }) { seekDvr(-30_000L) }
            MiniGuideControl(
                if (isPlaying) "❚❚" else "▶",
                enabled = true,
                modifier = Modifier.focusRequester(playFocus).focusProperties { down = downFromControls }
            ) {
                touch()
                if (Playback.simpleRaw) toast(context, "Pause needs DVR Live.")
                else player?.let { it.playWhenReady = !it.playWhenReady }
            }
            MiniGuideControl("30 ↷", enabled = dvrActive && isSeekable, modifier = Modifier.focusProperties { down = downFromControls }) { seekDvr(30_000L) }
            MiniGuideControl(if (ccEnabled) "CC ON" else "CC", modifier = Modifier.focusProperties { down = downFromControls }) { touch(); onToggleCc() }
            MiniGuideControl(if (afrEnabled) "AFR ON" else "AFR", modifier = Modifier.focusProperties { down = downFromControls }) { touch(); onToggleAfr() }
            MiniGuideControl(if (Playback.simpleRaw) "SMOOTH" else "DVR LIVE", modifier = Modifier.focusProperties { down = downFromControls }) { touch(); onToggleLiveMode() }
            Text(
                ProviderStreams.label(prefs),
                color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (dvrActive) {
            Text(
                buildString {
                    append("DVR started ")
                    if (Timeshift.startedAtWallMs > 0L) append(fmt.format(Date(Timeshift.startedAtWallMs))) else append("now")
                    append("  •  ")
                    append(shortTime(playerPosMs))
                    append(" watched  •  ")
                    append(shortTime(behindMs))
                    append(" behind LIVE")
                    if (!isSeekable) append("  •  seek initializing")
                },
                color = Muted, fontSize = 10.sp
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .tvFocus(RoundedCornerShape(8.dp))
                    .focusRequester(timelineFocus)
                    .focusProperties { up = playFocus; down = recentFocus }
                    .focusable()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.DirectionLeft -> { seekDvr(-30_000L); true }
                            Key.DirectionRight -> { seekDvr(30_000L); true }
                            else -> false
                        }
                    }
                    .padding(vertical = 8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { dvrProgress },
                    color = Accent,
                    trackColor = Color(0x554A4E5C),
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Text("DVR START", color = Muted, fontSize = 9.sp)
                Spacer(Modifier.weight(1f))
                Text(if (behindMs < 1500L) "LIVE" else "LIVE +${shortTime(behindMs)}", color = if (behindMs < 1500L) Live else Muted, fontSize = 9.sp)
            }
        } else {
            LinearProgressIndicator(
                progress = { bufferProgress },
                color = Accent,
                trackColor = Color(0x55202634),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            Text(
                "${Playback.livePathLabel()}  •  live buffer ${String.format(java.util.Locale.US, "%.1f", playerBufferMs / 1000.0)}s",
                color = Muted, fontSize = 10.sp
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${Playback.livePathLabel()}  •  DVR ${String.format(java.util.Locale.US, "%.0f", dvrBytes / 1_048_576.0)} MB",
                color = Muted, fontSize = 9.sp, modifier = Modifier.weight(1f)
            )
            Text(
                if (afrEnabled) {
                    val f = if (sourceFps > 0f) String.format(java.util.Locale.US, "%.2f", sourceFps) else "—"
                    val h = if (displayHz > 0f) String.format(java.util.Locale.US, "%.2f", displayHz) else "—"
                    "AFR $f→$h Hz"
                } else "AFR OFF",
                color = if (afrEnabled) Accent else Muted, fontSize = 9.sp
            )
        }

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Text("Recent channels", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(entries) { itemIndex, ch ->
                    val recentNow = remember(ch.url, EpgStore.loaded.value) {
                        val t = System.currentTimeMillis()
                        EpgStore.guide(ch.guideKey, ch.name).firstOrNull { t in it.startMs until it.endMs }?.title
                    }
                    Row(
                        Modifier
                            .width(210.dp)
                            .then(if (itemIndex == 0) Modifier.focusRequester(recentFocus) else Modifier)
                            .focusProperties { up = if (dvrActive) timelineFocus else playFocus }
                            .tvFocus(RoundedCornerShape(10.dp))
                            .background(Color(0x55202634), RoundedCornerShape(10.dp))
                            .clickable { touch(); onTune(ch) }
                            .padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelIcon(ch.name, ch.artwork, 44.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(recentNow ?: "Press OK to watch", color = if (recentNow != null) Muted else Accent, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun MiniGuideControl(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        label,
        color = if (enabled) Ink else Muted.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .tvFocus(RoundedCornerShape(20.dp))
            .background(Color(0x66202634), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

@Composable
private fun MiniGuideChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .tvFocus(RoundedCornerShape(20.dp))
            .background(Color(0x66202634), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/* ---------------------------------------------------------------------------
 * FRAME RATE MATCHING
 * Movies are 24fps, most live TV is 30/60, some sports 50. If the TV is locked
 * at 60Hz, 24fps content judders on slow pans (frames repeat unevenly). When
 * the setting is on, we ask the TV to switch to a refresh rate that divides
 * evenly into the video's frame rate — buttery pans, at the cost of a 1–2s
 * black HDMI resync when the rate changes. Only modes matching the current
 * resolution are considered, and if nothing divides cleanly we do nothing.
 * ------------------------------------------------------------------------- */
private fun clearFrameRateMatch(activity: android.app.Activity) {
    runCatching {
        val lp = activity.window.attributes
        if (lp.preferredDisplayModeId != 0) {
            lp.preferredDisplayModeId = 0
            activity.window.attributes = lp
        }
    }
}

private fun applyFrameRateMatch(activity: android.app.Activity, fps: Float) {
    runCatching {
        val display = activity.window.decorView.display ?: return
        val active = display.mode
        val candidates = display.supportedModes.filter {
            it.physicalWidth == active.physicalWidth && it.physicalHeight == active.physicalHeight
        }
        var best: android.view.Display.Mode? = null
        var bestScore = Float.MAX_VALUE
        for (m in candidates) {
            val ratio = m.refreshRate / fps
            val frac = kotlin.math.abs(ratio - kotlin.math.round(ratio))
            if (frac < 0.02f) {
                // Prefer the cleanest multiple, then the rate closest to the video.
                val score = frac * 100f + kotlin.math.abs(m.refreshRate - fps) / 1000f
                if (score < bestScore) { bestScore = score; best = m }
            }
        }
        val target = best ?: return
        if (target.modeId == active.modeId) return
        val lp = activity.window.attributes
        lp.preferredDisplayModeId = target.modeId
        activity.window.attributes = lp
    }
}
