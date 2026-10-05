// RYZOD_V467_NONBLOCKING_DVR
// RYZOD_V465_SAVED_ITEM_MENUS
// RYZOD_V464_SHARED_KEYBOARD_SEARCH_CATEGORY_ICONS
// RYZOD_V463_RECORD_DOWNLOAD_REVIEW_PASS
// RYZOD_V462_STREAM_DVR_KEYBOARD_STABILITY
// RYZOD_V460_ACTION_AND_FAST_START_POLISH
// RYZOD_V459_USER_REQUESTED_UI_FIXES\n// RYZOD_V457_FIRETV_CORRECTIONS
// RYZOD_V456_VISUAL_UNIFICATION
// RYZOD_V455_VERIFIED_FULL
package com.easyiptv.player

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.nativeKeyEvent
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ----------------------------- palette ----------------------------- */
private val Bg = Color(0xFF07111F)
private val SurfaceCol = Color(0xFF101C2D)
private val Surface2 = Color(0xFF16263A)
private val Line = Color(0xFF29445F)
private val Ink = Color(0xFFF2F3F5)
private val Muted = Color(0xFFBFEFFF)
private val Accent = Color(0xFFF5B944)
private val Live = Color(0xFFFF3B5C)
private val ProgramCyan = Color(0xFF58D9FF)
private val ElectricCyan = Color(0xFF49E8FF)
private val DeepBlue = Color(0xFF061424)
private val PanelGlow = Color(0xFF1C3853)
private val DownloadGreen = Color(0xFF35F06F)
private val NeonGreen = Color(0xFF39FF88) // ZAKO_V452_CHANNEL_GREEN
private val LimeBubble = Color(0xFFB8FF3D)

// ZAKO_V440_RING_SEEK_WINDOW: seek bounds come from retained ring bytes.
private const val DVR_REMOTE_SKIP_MS = 10_000L
private const val DVR_PRIME_BYTES = 512L * 1024L
private val BROWSE_PAGE_SIZE = CatalogRuntimePolicy.pageSize


private class DvrSeekAccelerator {
    data class Step(val deltaMs: Long, val gear: Int, val applyNow: Boolean)

    private var direction = 0
    private var gear = 0
    private var lastPressAt = 0L
    private var lastAppliedAt = 0L

    fun next(dir: Int, repeatCount: Int): Step {
        val now = android.os.SystemClock.uptimeMillis()
        val freshBurst = direction != dir || now - lastPressAt > 1_400L
        if (freshBurst) {
            gear = 0
        } else if (repeatCount == 0) {
            gear = (gear + 1) % JUMPS.size
        } else if (repeatCount > 0 && repeatCount % 6 == 0) {
            gear = (gear + 1).coerceAtMost(JUMPS.lastIndex)
        }
        direction = dir
        lastPressAt = now

        val apply = repeatCount == 0 || now - lastAppliedAt >= 380L
        if (apply) lastAppliedAt = now
        return Step(JUMPS[gear] * dir, gear, apply)
    }

    fun reset() {
        direction = 0
        gear = 0
        lastPressAt = 0L
        lastAppliedAt = 0L
    }

    companion object {
        private val JUMPS = longArrayOf(10_000L, 30_000L, 60_000L, 180_000L, 300_000L)

        fun label(gear: Int, dir: Int): String {
            val side = if (dir > 0) "FF" else "REW"
            return when (gear) {
                0 -> "$side 1×"
                1 -> "$side 2×"
                2 -> "$side 3×"
                3 -> "$side 4×"
                else -> "⚡ $side"
            }
        }
    }
}

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
            .graphicsLayer {
                scaleX = if (focused) 1.018f else 1f
                scaleY = if (focused) 1.018f else 1f
            }
            .background(
                color = if (focused) ElectricCyan.copy(alpha = 0.10f) else Color.Transparent,
                shape = shape
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) FocusPink else Color.Transparent,
                shape = shape
            )
    }

/**
 * Fire TV's View focus search can climb from Media3's seek bar to the playback
 * row and then get "stuck" there. Wire every visible stock-controller button
 * DOWN to the progress bar, and the progress bar UP to Play/Pause. This affects
 * only VOD / saved recordings / downloads; live TV uses RYZOD's own controller.
 */
@OptIn(UnstableApi::class)
private fun wireStockPlayerDpad(root: PlayerView) {
    val progress = root.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress) ?: return
    val play = root.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play_pause)
    progress.isFocusable = true
    progress.isFocusableInTouchMode = true
    if (progress.id != android.view.View.NO_ID && play != null && play.id != android.view.View.NO_ID) {
        progress.nextFocusUpId = play.id
    }
    progress.setOnKeyListener { _, keyCode, event ->
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && play != null) {
            play.requestFocus(); true
        } else false
    }
    fun walk(v: android.view.View) {
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        if (v !== progress && v.isFocusable && v.id != android.view.View.NO_ID && progress.id != android.view.View.NO_ID) {
            v.nextFocusDownId = progress.id
            // Fire OS sometimes ignores nextFocusDownId inside Media3's nested
            // controller. Intercept DOWN on the actual focused child as a hard
            // bridge back to the timeline. Other keys still belong to Media3.
            v.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    progress.requestFocus(); true
                } else false
            }
        }
    }
    walk(root)
}


/** One inexpensive CC switch for embedded/subtitle tracks Media3 already exposes. */
@OptIn(UnstableApi::class)
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
    var handler: ((Int, android.view.KeyEvent?) -> Boolean)? = null

    /** Checked BEFORE the on-screen views get the press — used for channel
     *  up/down zapping, which must win over the video view's own key handling. */
    var priority: ((Int) -> Boolean)? = null
}

private val zako447SearchDebounceMs = CatalogRuntimePolicy.searchDebounceMs

private val zako447MiniRows = LiveOverlay.visibleRowCount

private val zako447InitialDestination = TvShell.initialDestination()
private val zako447InitialFocus = TvShell.initialFocus()

private fun zako447StartupState(hasPlaylist: Boolean, loading: Boolean, error: Boolean) = StartupPolicy.state(hasPlaylist, loading, error)

private fun zako447AllowBackground(livePlaying: Boolean, playerBuffering: Boolean) = PlaybackResourcePolicy.allowBackgroundHeavyWork(livePlaying, playerBuffering)


@Composable
private fun RyzodBrandMark(compact: Boolean = true) {
    AsyncImage(
        model = R.drawable.ryzod_artwork_464,
        contentDescription = "RYZOD Media Player", contentScale = ContentScale.Fit,
        modifier = Modifier.size(if (compact) 46.dp else 62.dp)
    )
}

@Composable
private fun RyzodGridBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 54.dp.toPx()
        var x=0f
        while(x<=size.width){ drawLine(Color(0x102F6BFF),Offset(x,0f),Offset(x,size.height),1f); x+=step }
        var y=0f
        while(y<=size.height){ drawLine(Color(0x102F6BFF),Offset(0f,y),Offset(size.width,y),1f); y+=step }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // RYZOD low-memory profile: cap poster/logo RAM cache on Fire TV.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .memoryCache {
                    coil.memory.MemoryCache.Builder(this)
                        .maxSizePercent(0.06)
                        .build()
                }
                .crossfade(false)
                .build()
        )
        StabilityCore.install(this)
        StabilityCore.note("activity_create")
        // ZAKO_V449_STARTUP_REARM
        ScheduleStore.rearmAll(this, getSharedPreferences("easyiptv", MODE_PRIVATE))
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0A1D33), DeepBlue, Bg)
                            )
                        )
                ) {
                    App()
                }
            }
        }
    }

    // This is the public Activity input entry point. ComponentActivity inherits
    // an AndroidX-internal annotation on its implementation; dispatch must still
    // delegate to super so Compose and the platform receive unhandled keys.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // A cached playlist may be visible before the fresh provider refresh is
        // finished. During that short window, swallow remote input so nobody can
        // drive into half-built lists and trigger the startup crash/glitch path.
        if (AppInputGate.startupLocked) return true
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            PlayerKeys.priority?.invoke(event.keyCode) == true
        ) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (PlayerKeys.handler?.invoke(keyCode, event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        Playback.resumeFromBackground()
        ScheduleStore.rearmAll(this,getSharedPreferences("easyiptv",MODE_PRIVATE))
    }

    override fun onStop() {
        StabilityCore.beforeBackground()
        BackgroundWorkSupervisor.cancelNonEssential()
        // Fire TV storage + provider safety: when RYZOD is hidden, stop the live
        // provider/DVR path instead of quietly writing video in the background.
        // The only exception is an active recording, which owns the one stream.
        if (isChangingConfigurations) Playback.pauseForConfiguration()
        else Playback.suspendForBackground()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        StabilityCore.onTrimMemory(level)
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // Posters can consume a meaningful chunk of RAM on Fire TV. They are
            // disposable network/cache images, so drop only Coil's MEMORY cache
            // when Android says pressure is building. Disk cache and playback stay.
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
    }

    override fun onLowMemory() {
        StabilityCore.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        Playback.releaseAll()
        StabilityCore.shutdown()
        super.onDestroy()
    }
}

/* ----------------------------- root ----------------------------- */
@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("easyiptv", Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) { prefs.edit().putBoolean("autoplay_last", false).apply() }

    var playlists by remember { mutableStateOf(PlaylistStore.load(prefs)) }
    var activeIdx by remember { mutableIntStateOf(PlaylistStore.activeIndex(prefs)) }
    var nav by remember { mutableStateOf<Nav>(Nav.Home) }
    var data by remember { mutableStateOf<AppData?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // Cached data may paint immediately, but interaction stays locked until the
    // fresh live refresh has completed (successfully or with a safe cached fallback).
    var startupSettled by remember(activeIdx, reload) { mutableStateOf(false) }

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
    var catalogSection by remember(activeIdx, reload) { mutableStateOf(CatalogRefresh.Section.ALL) }

    LaunchedEffect(railSection) {
        StabilityCore.noteScreen(railSection)
    }

    // One-time "external drive found — use it?" prompt. Shows only if a drive is
    // plugged in, the setting is off, and we haven't asked about THIS drive yet.
    var showDrivePrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)   // let the app settle first
        val present = Storage.drivePresent(context)
        val asked = prefs.getBoolean("ext_prompt_shown", false)
        if (present && !Storage.isEnabled(prefs) && !asked) showDrivePrompt = true
    }
    if (showDrivePrompt && startupSettled) {
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
                        "No provider stream is free for that change. Your RYZOD limit is $maxStreams; stop a recording/download or raise it only if your IPTV plan allows more."
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
        val currentLive = Playback.queue.getOrNull(Playback.currentIdxC.intValue)
        val keepSession = target?.isLive == true && currentLive?.isLive == true &&
            target.url == currentLive.url && Playback.player != null && Playback.liveMode
        nav = if (keepSession) p.copy(queue = Playback.queue, start = Playback.currentIdxC.intValue, attach = true) else p
    }

    LaunchedEffect(Unit) {
        // ZAKO_V432_STARTUP_POLISH: first paint + Live lineup win the startup race.
        // Nonessential disk cleanup/queue maintenance starts after the UI settles.
        kotlinx.coroutines.delay(2_200L)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DownloadStore.migrateLegacyRetention(prefs)
            DownloadStore.migrateLegacyEngine(context, prefs)
            DownloadStore.cleanup(context, prefs)
            DownloadStore.kickQueue(context, prefs)
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
                loadError = e.message ?: "Provider refresh unavailable"
            } finally {
                // Unlock only after the fresh provider attempt has finished. If
                // it failed but a cache exists, the cache is now a deliberate
                // fallback rather than an accidental half-loaded startup state.
                startupSettled = true
            }
        }
    }

    // XMLTV can be huge. Never download/parse the full guide behind full-screen
    // playback. PlayerScreen uses the provider's tiny now/next request instead.
    // Load XMLTV only when the viewer is actually browsing Live/Search, and
    // Simple Mode skips it entirely to protect troublesome channels.
    LaunchedEffect(data, source, nav, railSection) {
        val s = source ?: return@LaunchedEffect
        if (data == null || nav is Nav.Play) return@LaunchedEffect
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
    LaunchedEffect(railSection, source, activeIdx, startupSettled, nav, catalogRetry) {
        val s = source ?: return@LaunchedEffect
        val needsCatalog = railSection == "movies" || railSection == "series" || railSection == "search"
        val liveBase = data ?: return@LaunchedEffect
        if (!startupSettled || nav is Nav.Play || !needsCatalog || catalogLoadedThisSession ||
            (!s.supportsSeries && catalogRetry==0)) return@LaunchedEffect
        val job=kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        if(job!=null) BackgroundWorkSupervisor.replace("catalog",job)
        catalogLoading=true
        catalogError=null
        try {
            val result=CatalogRefresh.load(s,liveBase,catalogSection)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            // Finish persistence before publishing UI state; changing data must
            // not cancel its own save or trigger another catalog request.
            if(cacheKey!=null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                DataCache.save(context,cacheKey,result.data)
            }
            data=result.data
            catalogError=result.warning
            catalogLoadedThisSession=true
        } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled
        } catch(_:Exception) {
            catalogError="Catalog update failed. Saved titles are still available; try Update again."
            catalogLoadedThisSession=true
        } finally {catalogLoading=false}
    }

    // Restore the recent-channel surf strip across app restarts. This is done
    // only when playlist data changes — zero periodic work while watching.
    LaunchedEffect(data, activeIdx) {
        data?.live?.let { channels ->
            RecentChannels.restore(prefs, channels.map { livePlayable(prefs, it) })
        }
    }

    // RYZOD_V460_FAST_START: never open a provider stream during app startup.
    // The viewer chooses Live TV after the lightweight playlist/guide shell is ready.

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
        !startupSettled -> StartupLoadingScreen(playlists.getOrNull(activeIdx)?.name.orEmpty())
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
                catalogSection=CatalogRefresh.Section.ALL
                catalogError = null
                catalogLoadedThisSession = false
                catalogRetry++
            },
            onRefreshMovies = {
                catalogSection=CatalogRefresh.Section.MOVIES
                catalogLoadedThisSession=false
                catalogRetry++
            },
            onRefreshSeries = {
                catalogSection=CatalogRefresh.Section.SERIES
                catalogLoadedThisSession=false
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
                RyzodBrandMark(compact = false)
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
@OptIn(UnstableApi::class)
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
    onRefreshMovies: () -> Unit,
    onRefreshSeries: () -> Unit,
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
    val guideRefreshScope = rememberCoroutineScope()
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
            title = { Row(verticalAlignment = Alignment.CenterVertically) { RyzodBrandMark(compact = true); Spacer(Modifier.width(10.dp)); Text("Exit?", color = Ink) } },
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

    val railFocus = remember { FocusRequester() }
    var railReturnRequest by remember { mutableIntStateOf(0) }
    val safeData = data ?: AppData(
        liveCats = emptyList(), live = emptyList(),
        vodCats = emptyList(), movies = emptyList(),
        seriesCats = emptyList(), series = emptyList()
    )

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                RyzodBrandMark(compact = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(22.dp).height(3.dp).background(Accent, RoundedCornerShape(3.dp)))
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
            if (loadError != null) {
                Text(
                    "OFFLINE / SAVED",
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
            Text(clock, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        }

        when {
            false && data == null && loadError == null -> Box(Modifier.weight(1f)) { LoadingBox("Loading your playlist…") }
            false && loadError != null -> Box(Modifier.weight(1f)) { ErrorBox(loadError ?: "Provider refresh unavailable", onRetry) }
            else -> Row(Modifier.weight(1f)) {
                HomeRail(
                    data = safeData,
                    section = section,
                    depth = depth,
                    liveCat = liveCat,
                    movieCat = movieCat,
                    seriesCat = seriesCat,
                    onRoot = onRoot,
                    onBackToRoot = onBackToRoot,
                    externalFocus = railFocus,
                    returnRequest = railReturnRequest,
                    onCat = { id ->
                        when (section) {
                            "live" -> onLiveCat(id)
                            "movies" -> onMovieCat(id)
                            else -> onSeriesCat(id)
                        }
                    }
                )
                Box(Modifier.weight(1f)) {
                    // ZAKO_V425_FOCUS_FIX: panes own Left at their true boundary.
                    if (catalogLoading && (section == "movies" || section == "series")) {
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
                        data == null && loadError == null &&
                            (section == "live" || section == "movies" || section == "series" || section == "search") ->
                            LoadingBox("Loading your playlist…")
                        data == null && loadError != null &&
                            (section == "live" || section == "movies" || section == "series" || section == "search") ->
                            ErrorBox(
                                err = (loadError ?: "No internet connection") +
                                    "\n\nDownloads and saved recordings still work. Choose them from the left menu.",
                                onRetry = onRetry,
                                title = "Live service unavailable"
                            )
                        !catalogLoading && catalogError != null &&
                            (section == "movies" || section == "series") &&
                            safeData.movies.isEmpty() && safeData.series.isEmpty() ->
                            ErrorBox(
                                err = catalogError ?: "On-demand catalog request failed",
                                onRetry = onRetryCatalog,
                                title = "Couldn't load Movies & Series"
                            )
                        depth == 1 && section == "live" -> LivePane(prefs, activeIdx, safeData, liveCat, onPlayLive,
                            onLeftToRail = { railReturnRequest++ }, onRefreshGuide = {
                                guideRefreshScope.launch { EpgStore.load(source?.xmltvUrl(), force = true) }
                            })
                        depth == 1 && section == "movies" -> MoviesPane(source, prefs, safeData, movieCat, onPlay, onLeftToRail = { railReturnRequest++ }, onRefresh=onRefreshMovies, refreshing=catalogLoading, refreshWarning=catalogError)
                        depth == 1 && section == "series" -> SeriesPane(source, safeData, seriesCat, onSeries, onLeftToRail = { railReturnRequest++ }, onRefresh=onRefreshSeries, refreshing=catalogLoading, refreshWarning=catalogError)
                        section == "search" -> SearchTab(
                            source, prefs, safeData, searchQuery, onSearchQuery, onPlay, onPlayLive, onSeries,
                            onDemandWarning = if (!catalogLoading) catalogError else null
                        )
                        section == "downloads" -> Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { railReturnRequest++; true } else false }) { DownloadsPane(prefs, onPlay) }
                        section == "recordings" -> Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { railReturnRequest++; true } else false }) { RecordingsPane(prefs, onPlay) }
                        section == "playlists" -> Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { railReturnRequest++; true } else false }) { PlaylistsPane(playlists, activeIdx, onSelectPlaylist, onDeletePlaylist, onAddPlaylist) }
                        section == "settings" -> Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { railReturnRequest++; true } else false }) { SettingsPane(prefs, onModeChanged = onRetry) }
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
                if (mini != null && Playback.player != null && Recorder.activeName.value == null) {
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
                                                Playback.attachVideoView(this)
                                            }
                                        },
                                        update = { it.player = Playback.player },
                                        // The shared player outlives this corner view. Remove
                                        // its listeners when returning to full-screen playback.
                                        onRelease = { Playback.detachVideoView(it); it.player = null },
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
    externalFocus: FocusRequester,
    returnRequest: Int,
    onCat: (String) -> Unit
) {
    val railState = androidx.compose.foundation.lazy.rememberLazyListState()
    val cats = when (section) { "live" -> data.liveCats; "movies" -> data.vodCats; else -> data.seriesCats }
    val extras = when (section) {
        "live" -> listOf("fav" to "★ Favorites", "all" to "All channels")
        "movies" -> listOf("all" to "All movies")
        else -> listOf("all" to "All series")
    }
    val selected = when (section) { "live" -> liveCat; "movies" -> movieCat; else -> seriesCat }
    val categoryIds = extras.map { it.first } + cats.map { it.id }
    val selectedIndex = categoryIds.indexOf(selected).coerceAtLeast(0)
    suspend fun restoreRailFocus() {
        val index = if (depth == 0) RootItems.indexOfFirst { it.first == section }.coerceAtLeast(0) else selectedIndex + 1
        railState.scrollToItem(index)
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        runCatching { externalFocus.requestFocus() }
    }
    LaunchedEffect(depth, section) { if (depth == 0) restoreRailFocus() }
    LaunchedEffect(returnRequest) { if (returnRequest > 0) restoreRailFocus() }
    LazyColumn(
        state = railState,
        modifier = Modifier
            .width(126.dp)
            .fillMaxHeight()
            .background(SurfaceCol)
            .onKeyEvent { ev ->
                if (depth == 1 && ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                    onBackToRoot()
                    true
                } else false
            },
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        if (depth == 0) {
            itemsIndexed(RootItems) { pos, p ->
                RailItem(
                    p.second, section == p.first,
                    modifier = if (p.first == section) Modifier.focusRequester(externalFocus) else Modifier
                ) { onRoot(p.first) }
            }
        } else {
            item { RailItem("←  Main menu", false) { onBackToRoot() } }
            itemsIndexed(extras) { ix, p ->
                RailItem(
                    p.second, selected == p.first,
                    modifier = if (selectedIndex == ix) Modifier.focusRequester(externalFocus) else Modifier
                ) { onCat(p.first) }
            }
            itemsIndexed(cats) { ix, c ->
                RailItem(
                    c.name, selected == c.id,
                    modifier = if (selectedIndex == extras.size + ix) Modifier.focusRequester(externalFocus) else Modifier
                ) { onCat(c.id) }
            }
        }
    }
}

@Composable
private fun RailItem(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // ZAKO_V432_LIME_BUBBLES: lightweight rounded category bubbles, no blur/shaders.
    Row(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .fillMaxWidth()
            .tvFocus(RoundedCornerShape(24.dp))
            .background(
                if (active) LimeBubble.copy(alpha = 0.22f) else PanelGlow.copy(alpha = 0.60f),
                RoundedCornerShape(24.dp)
            )
            .border(
                if (active) 2.dp else 1.dp,
                if (active) LimeBubble else ElectricCyan.copy(alpha = 0.22f),
                RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(if (active) 8.dp else 6.dp)
                .background(if (active) LimeBubble else ElectricCyan.copy(alpha = 0.65f), CircleShape)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = if (active) LimeBubble else Ink,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp)
        )
    }
}

@Composable
private fun StartupLoadingScreen(playlistName: String) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RyzodBrandMark(compact = false)
        Spacer(Modifier.height(18.dp))
        CircularProgressIndicator(color = Accent)
        Spacer(Modifier.height(18.dp))
        Text("Preparing your TV experience…", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(
            "Please wait while RYZOD refreshes your channels and gets everything ready for smooth browsing and playback.",
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
    onPlayLive: (List<Playable>, Int) -> Unit,
    onLeftToRail: () -> Unit = {},
    onRefreshGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val favKey = "fav_live_$activeIdx"
    var favs by remember(activeIdx) { mutableStateOf(prefs.getStringSet(favKey, emptySet())?.toSet() ?: emptySet()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var manualListChannel by remember { mutableStateOf<LiveChannel?>(null) }
    manualListChannel?.let { ch -> ManualRecordingDialog(prefs,ch,onClose={manualListChannel=null}) }
    // ZAKO_V431_LIVE_INFO
    var liveInfo by remember { mutableStateOf<Pair<LiveChannel, EpgEntry?>?>(null) }
    var showGridGuide by remember { mutableStateOf(true) }
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val guideLoading = EpgStore.loading.value
    val guideReady = EpgStore.loaded.value

    fun toggleFav(id: String) {
        val n = favs.toMutableSet()
        if (!n.add(id)) n.remove(id)
        favs = n
        prefs.edit().putStringSet(favKey, n).apply()
    }

    // ZAKO_V433_REMEMBER_FILTERS: category lists only rebuild when their inputs change.
    val filtered = remember(data.live, selectedCat, favs) {
        data.live.filter { c ->
            when (selectedCat) {
                "all" -> true
                "fav" -> favs.contains(c.id)
                else -> c.categoryId == selectedCat
            }
        }
    }

    // ZAKO_V432_LIVE_QUEUE: map this category once, not on every channel click.
    val playableQueue = remember(filtered, activeIdx) { filtered.map { livePlayable(prefs, it) } }

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
    // ZAKO_V425_LIVE_WRAP: one requester per row lets first/last wrap reliably.
    val rowFocusers = remember(selectedCat, filtered.size) {
        List(filtered.size.coerceAtLeast(1)) { FocusRequester() }
    }
    val liveNavScope = rememberCoroutineScope()
    LaunchedEffect(selectedCat, currentIdxInList, filtered.size) {
        if (currentIdxInList >= 0 && currentIdxInList < rowFocusers.size) {
            kotlinx.coroutines.delay(120)
            runCatching { listState.scrollToItem(currentIdxInList) }
            kotlinx.coroutines.delay(80)
            runCatching { rowFocusers[currentIdxInList].requestFocus() }
        }
    }

    // ZAKO_V431_LIVE_INFO action sheet uses only the already-loaded EPG row.
    liveInfo?.let { pair ->
        val infoChannel = pair.first
        val entry = pair.second
        val recordable = infoChannel.url.endsWith(".ts")
        AlertDialog(
            onDismissRequest = { liveInfo = null },
            containerColor = SurfaceCol,
            title = { Text(entry?.title ?: infoChannel.name, color = Ink, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text(infoChannel.name, color = ProgramCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (entry != null) {
                        Text("${fmt.format(Date(entry.startMs))}–${fmt.format(Date(entry.endMs))}", color = Ink, fontSize = 11.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(entry.desc.ifBlank { "No description was supplied by the guide." }, color = Ink, fontSize = 12.sp)
                    } else {
                        Text("Program information is not available for this channel right now.", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)),
                        onClick = { toggleFav(infoChannel.id) }
                    ) {
                        Text(if (favs.contains(infoChannel.id)) "★ REMOVE FAVORITE" else "☆ ADD FAVORITE", color = Accent, fontWeight = FontWeight.Bold)
                    }
                    if (recordable && entry != null) {
                        TextButton(
                            modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)),
                            onClick = {
                                val airing = System.currentTimeMillis() in entry.startMs until entry.endMs
                                if (airing) {
                                    Recorder.start(context, infoChannel.url, "${entry.title} (${infoChannel.name})", entry.endMs + 2 * 60 * 1000)
                                    toast(context, "Recording ${entry.title}.")
                                    liveInfo = null
                                } else {
                                    toast(context, ScheduleStore.add(context, prefs, entry.title, infoChannel.name, infoChannel.url, entry.startMs, entry.endMs))
                                    liveInfo = null
                                }
                            }
                        ) { Text("● RECORD", color = Live, fontWeight = FontWeight.Bold) }
                    }
                }
            },
            confirmButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(16.dp)), onClick = { liveInfo = null }) {
                    Text("CLOSE", color = Ink)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("RYZOD GUIDE", color=ProgramCyan, fontSize=12.sp, fontWeight=FontWeight.ExtraBold)
            TextButton(onClick = onRefreshGuide, enabled = !guideLoading,
                modifier = Modifier.tvFocus(RoundedCornerShape(12.dp))) {
                Text(if (guideLoading) "UPDATING…" else "↻ UPDATE GUIDE", color = Ink, fontSize = 11.sp)
            }
        }
        Text("Guide information comes directly from your provider. Updates may take 1–2 minutes or longer for large guides.",
            color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp))
        EpgStore.status.value.takeIf { it.isNotBlank() }?.let { message ->
            Text(message, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (guideLoading) {
            Text(
                "Downloading TV guide… this can take a minute or two.",
                fontSize = 11.sp, color = Muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        if (showGridGuide && filtered.isNotEmpty()) {
            LiveGridGuide(
                prefs = prefs, channels = filtered,
                favs = favs, onToggleFavorite = { toggleFav(it) },
                onPlayLive = onPlayLive,
                onClose = { }
            )
        } else if (filtered.isEmpty()) {
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
                            .focusRequester(rowFocusers[chIdx])
                            .onPreviewKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    ev.key == Key.DirectionLeft -> { onLeftToRail(); true }
                                    ev.key == Key.DirectionUp && chIdx == 0 && filtered.size > 1 -> {
                                        liveNavScope.launch {
                                            val target = filtered.lastIndex
                                            listState.scrollToItem(target)
                                            kotlinx.coroutines.delay(40)
                                            runCatching { rowFocusers[target].requestFocus() }
                                        }
                                        true
                                    }
                                    ev.key == Key.DirectionDown && chIdx == filtered.lastIndex && filtered.size > 1 -> {
                                        liveNavScope.launch {
                                            listState.scrollToItem(0)
                                            kotlinx.coroutines.delay(40)
                                            runCatching { rowFocusers[0].requestFocus() }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .tvFocus()
                            .background(SurfaceCol, RoundedCornerShape(14.dp))
                            .clickable {
                                // Hand the player this WHOLE category, starting on this
                                // channel — that's what makes channel up/down work.
                                onPlayLive(playableQueue, chIdx.coerceIn(0, (playableQueue.size - 1).coerceAtLeast(0)))
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
                                        color = Color(0xFFFFE45C), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)),
                                onClick = { liveInfo = ch to current }
                            ) {
                                Text("ⓘ", color = NeonGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                            // ZAKO_V450_UNIVERSAL_GUIDE: every playlist channel always has
                            // a guide/timer entry, even when the provider supplies zero EPG rows.
                            IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                                expandedId = if (expandedId == ch.id) null else ch.id
                            }) {
                                Icon(
                                    Icons.Filled.Today,
                                    contentDescription = if (schedule.isNotEmpty()) "See what's on later" else "Open time guide",
                                    tint = if (expandedId == ch.id) Accent else Muted
                                )
                            }
                            IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = { toggleFav(ch.id) }) {
                                Icon(
                                    if (favs.contains(ch.id)) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (favs.contains(ch.id)) Accent else Muted
                                )
                            }
                        }
                        if (expandedId == ch.id) {
                            Spacer(Modifier.height(6.dp))
                            val dayFmt = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
                            Chip("Manual timer • up to 7 days",false) { manualListChannel=ch }
                            schedule.take(30).forEach { e ->
                                val isNow = now in e.startMs until e.endMs
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        dayFmt.format(Date(e.startMs)),
                                        fontSize = 12.sp,
                                        color = if (isNow) Accent else ElectricCyan,
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


@Composable
private fun LiveGridGuide(
    prefs: SharedPreferences,
    channels: List<LiveChannel>,
    favs: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onPlayLive: (List<Playable>, Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var page by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<LiveChannel, EpgEntry>?>(null) }
    val returnUrl = prefs.getString("last_live_url", null)
    val returnIndex = channels.indexOfFirst { it.url == returnUrl }.coerceAtLeast(0)
    val returnFocus = remember(returnUrl, channels.size) { FocusRequester() }
    val guideListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(returnUrl, channels.size) {
        if (channels.isNotEmpty()) {
            runCatching { guideListState.scrollToItem(returnIndex.coerceIn(0, channels.lastIndex)) }
            kotlinx.coroutines.delay(120)
            runCatching { returnFocus.requestFocus() }
        }
    }
    var manualChannel by remember { mutableStateOf<LiveChannel?>(null) }
    BackHandler { onClose() }

    val guideRevision = EpgStore.revision.intValue
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val halfHour = 30L * 60L * 1000L
    val base = remember { now }
    val windowStart = base + page * 4L * halfHour

    val firstCells = remember(channels) { channels.map { FocusRequester() } }
    val lastCells = remember(channels) { channels.map { FocusRequester() } }
    val channelCells = remember(channels) { channels.map { FocusRequester() } }
    var pendingFocus by remember { mutableStateOf<Pair<Int,Boolean>?>(null) }
    var focusRequest by remember { mutableIntStateOf(0) }
    fun rowFullyVisible(row: Int): Boolean {
        val layout=guideListState.layoutInfo
        val item=layout.visibleItemsInfo.firstOrNull {it.index==row} ?: return false
        return item.offset>=layout.viewportStartOffset && item.offset+item.size<=layout.viewportEndOffset
    }
    fun moveTo(row: Int, last: Boolean=false, afterPageChange:Boolean=false) {
        if(!afterPageChange && rowFullyVisible(row)) {
            if(runCatching { (if(last) lastCells[row] else firstCells[row]).requestFocus() }.isSuccess) return
        }
        pendingFocus=row to last;focusRequest++
    }
    LaunchedEffect(focusRequest) {
        val target=pendingFocus ?: return@LaunchedEffect
        // A composed row can still be clipped. Reveal it before focus so the
        // default animated bring-into-view does not fight the guide's D-pad move.
        if(!rowFullyVisible(target.first)) guideListState.scrollToItem(target.first)
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        runCatching { (if(target.second) lastCells[target.first] else firstCells[target.first]).requestFocus() }
        pendingFocus=null
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(SimpleDateFormat("EEE M/d",Locale.getDefault()).format(Date(windowStart)), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(142.dp))
            repeat(4) { slot ->
                Text(
                    fmt.format(Date(windowStart + slot * halfHour)),
                    color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // RYZOD_V459_GUIDE_SEPARATORS
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=.55f)))
        LazyColumn(
            state = guideListState,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { chIndex, ch ->
                val schedule = remember(ch.id, windowStart, guideRevision) { EpgStore.guide(ch.epgId, ch.name, windowStart) }
                val cells = remember(schedule,windowStart) { GuideGeometry.cells(schedule,windowStart,windowStart+GuideNavigation.WINDOW_MS) }
                val cellFocus = remember(ch.id,cells.size) { cells.map { FocusRequester() } }
                Row(
                    Modifier.fillMaxWidth().height(58.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.width(142.dp).fillMaxHeight()
                            .then(if(ch.url==returnUrl) Modifier.focusRequester(returnFocus) else Modifier)
                            .border(if(ch.url==returnUrl) 3.dp else 1.dp,if(ch.url==returnUrl) Accent else Color.White.copy(alpha=.45f),RoundedCornerShape(8.dp))
                            .background(ChannelOrange, RoundedCornerShape(8.dp))
                            .focusRequester(channelCells[chIndex])
                            .onPreviewKeyEvent { ev ->
                                if(ev.type!=KeyEventType.KeyDown) false else when(ev.key) {
                                    Key.DirectionRight -> { moveTo(chIndex); true }
                                    Key.DirectionDown -> { moveTo(GuideNavigation.row(chIndex,1,channels.size)); true }
                                    Key.DirectionUp -> { moveTo(GuideNavigation.row(chIndex,-1,channels.size)); true }
                                    else -> false
                                }
                            }
                            .tvFocus(RoundedCornerShape(8.dp)).clickable { manualChannel = ch }.padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelIcon(ch.name, ch.icon, 30.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(ch.name, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.weight(1f).fillMaxHeight()) {
                        cells.forEachIndexed { cellIndex, cell ->
                            val entry = cell.entry
                            val airing = entry != null && now in entry.startMs until entry.endMs
                            Box(Modifier.weight((cell.endMs - cell.startMs).toFloat()).fillMaxHeight()
                                .padding(start = 2.dp)
                                .focusRequester(cellFocus[cellIndex])
                                .then(if(cellIndex==0) Modifier.focusRequester(firstCells[chIndex]) else Modifier)
                                .then(if(cellIndex==cells.lastIndex) Modifier.focusRequester(lastCells[chIndex]) else Modifier)
                                .onPreviewKeyEvent { ev ->
                                    if(ev.type!=KeyEventType.KeyDown) false else when(ev.key) {
                                        Key.DirectionDown -> { moveTo(GuideNavigation.row(chIndex,1,channels.size)); true }
                                        Key.DirectionUp -> { moveTo(GuideNavigation.row(chIndex,-1,channels.size)); true }
                                        Key.DirectionRight -> {
                                            if(cellIndex<cells.lastIndex) cellFocus[cellIndex+1].requestFocus()
                                            else if(page<GuideNavigation.LAST_PAGE) { page=GuideNavigation.page(page,1); moveTo(chIndex,afterPageChange=true) }
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            if(cellIndex>0) cellFocus[cellIndex-1].requestFocus()
                                            else if(page>0) { page=GuideNavigation.page(page,-1);moveTo(chIndex,true,afterPageChange=true) }
                                            else channelCells[chIndex].requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .tvFocus(RoundedCornerShape(4.dp))
                                .background(if (airing) ProgramCyan.copy(alpha = 0.20f) else Surface2, RoundedCornerShape(4.dp))
                                .clickable { selected = ch to (entry ?: EpgEntry("Manual Recording",
                                    "No program information from provider.", cell.startMs, cell.endMs)) }
                                .padding(horizontal = 6.dp, vertical = 5.dp)) {
                                if (entry == null) Text("No information", color = Muted, fontSize = 10.sp, maxLines = 1)
                                else Column {
                                    Text(entry.title, color = Color(0xFFFFE45C), fontSize = 10.sp,
                                        fontWeight = if (airing) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${fmt.format(Date(entry.startMs))}–${fmt.format(Date(entry.endMs))}",
                                        color = Ink, fontSize = 8.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    manualChannel?.let { ch -> ManualRecordingDialog(prefs,ch,onClose={manualChannel=null}) }

    selected?.let { pair ->
        val ch = pair.first
        val entry = pair.second
        val airing = System.currentTimeMillis() in entry.startMs until entry.endMs
        val recordable = ch.url.endsWith(".ts")
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = SurfaceCol,
            title = { Text(entry.title, color = Ink, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text(ch.name, color = ProgramCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${fmt.format(Date(entry.startMs))}–${fmt.format(Date(entry.endMs))}",
                        color = Ink, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        entry.desc.ifBlank { "No description was supplied in the lightweight guide." },
                        color = Ink, fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                // RYZOD_V459_ONE_ACTION_ROW
                Row(horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(airing) TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                        val queue=channels.map{livePlayable(prefs,it)};selected=null;onPlayLive(queue,chIndexOf(channels,ch))
                    }) { Text("▶ WATCH",color=ProgramCyan,fontWeight=FontWeight.Bold,fontSize=10.sp) }
                    if(recordable) TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={
                        if(airing) {
                            Recorder.start(context,ch.url,entry.title+" ("+ch.name+")",entry.endMs+2*60*1000);toast(context,"Recording "+entry.title+".");selected=null
                        } else { toast(context,ScheduleStore.add(context,prefs,entry.title,ch.name,ch.url,entry.startMs,entry.endMs));selected=null }
                    }) { Text(if(airing)"● RECORD" else "● SCHEDULE",color=Live,fontWeight=FontWeight.Bold,fontSize=10.sp) }
                    TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={onToggleFavorite(ch.id)}) {
                        Text(if(favs.contains(ch.id))"★ FAVORITE" else "☆ FAVORITE",color=Accent,fontWeight=FontWeight.Bold,fontSize=10.sp)
                    }
                    TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(14.dp)),onClick={selected=null}) { Text("CLOSE",color=Ink,fontSize=10.sp) }
                }
            },
            dismissButton = {}
        )
    }
}

@Composable
private fun ManualRecordingDialog(prefs:SharedPreferences,ch:LiveChannel,onClose:()->Unit,onSaved:()->Unit={}) {
    val context=LocalContext.current
    var day by remember { mutableIntStateOf(0) }
    var hour by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf((java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)/5)*5) }
    var duration by remember { mutableIntStateOf(60) }
    var confirmation by remember { mutableStateOf<Pair<Long,Long>?>(null) }
    val chosen=java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR,day);set(java.util.Calendar.HOUR_OF_DAY,hour);set(java.util.Calendar.MINUTE,minute)
        set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)
    }
    val end=ManualRecordingDuration.end(chosen.timeInMillis,duration)
    val fmt=remember { SimpleDateFormat("EEE h:mm a",Locale.getDefault()) }
    val request=confirmation
    if(request!=null) {
        AlertDialog(onDismissRequest={confirmation=null},containerColor=SurfaceCol,
            title={Text("Are you sure?",color=Ink,fontWeight=FontWeight.Bold)},
            text={Text("Record ${ch.name} from ${fmt.format(Date(request.first))} until ${fmt.format(Date(request.second))} (${ManualRecordingDuration.label(duration)})?",color=Ink)},
            confirmButton={TextButton(modifier=Modifier.tvFocus(),onClick={
                toast(context,ScheduleStore.add(context,prefs,"Manual Recording",ch.name,ch.url,request.first,request.second));onSaved();onClose()
            }){Text("YES, RECORD",color=Live,fontWeight=FontWeight.Bold)}},
            dismissButton={TextButton(modifier=Modifier.tvFocus(),onClick={confirmation=null}){Text("CLOSE",color=Ink)}})
    } else AlertDialog(onDismissRequest=onClose,containerColor=SurfaceCol,
        title={Text("Schedule "+ch.name,color=Ink,fontWeight=FontWeight.Bold)},
        text={Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Day • next 7 days",color=Muted,fontSize=11.sp)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) { items(7) { d ->
                val date=java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR,d) }
                Chip(SimpleDateFormat(if(d==0) "'Today'" else "EEE M/d",Locale.getDefault()).format(date.time),day==d){day=d}
            } }
            Text("Hour • 24 hours",color=Muted,fontSize=11.sp)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) { items(24) { h ->
                val date=(chosen.clone() as java.util.Calendar).apply { set(java.util.Calendar.HOUR_OF_DAY,h) }
                Chip(SimpleDateFormat("h a",Locale.getDefault()).format(date.time),hour==h){hour=h}
            } }
            Text("Minutes • every 5 minutes",color=Muted,fontSize=11.sp)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) { items(12) { i ->
                Chip(String.format(Locale.US,"%02d",i*5),minute==i*5){minute=i*5}
            } }
            Text("Stop after",color=Muted,fontSize=11.sp)
            // Two fixed rows keep all ten durations reachable and visible on TV.
            ManualRecordingDuration.minutes.chunked(5).forEach { group ->
                Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) { group.forEach { mins ->
                    Chip(ManualRecordingDuration.label(mins),duration==mins){duration=mins}
                } }
            }
            Text("Start: ${fmt.format(chosen.time)}\nStop: ${fmt.format(Date(end))}",color=Accent,fontWeight=FontWeight.Bold,fontSize=12.sp)
        }},
        confirmButton={TextButton(modifier=Modifier.tvFocus(),onClick={
            val error=ScheduleStore.validateManual(chosen.timeInMillis,end)
            if(error!=null) toast(context,error) else confirmation=chosen.timeInMillis to end
        }){Text("RECORD",color=Live,fontWeight=FontWeight.Bold)}},
        dismissButton={TextButton(modifier=Modifier.tvFocus(),onClick=onClose){Text("CLOSE",color=Ink)}})
}

private fun chIndexOf(channels: List<LiveChannel>, target: LiveChannel): Int =
    channels.indexOfFirst { it.id == target.id && it.url == target.url }.coerceAtLeast(0)


/* The timeshift DVR records the classic (.ts) live stream — the one every
 * provider supports and the only one that can be recorded. */
private fun tsUrl(url: String): String =
    if (url.endsWith(".m3u8")) url.removeSuffix(".m3u8") + ".ts" else url

// ZAKO_V437_WEAK_CHANNEL_CORE: Steady mode uses the provider's HLS form when
// available. HLS segment retries tolerate bursty/overloaded sports feeds much
// better than a single long MPEG-TS socket. If HLS is rejected, Playback falls
// back to classic TS automatically for that channel.
private fun hlsUrl(url: String): String = when {
    url.endsWith(".ts", ignoreCase = true) -> url.dropLast(3) + ".m3u8"
    else -> url
}

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
        while (items.size > 8) items.removeAt(items.size - 1)
    }

    /** A channel becomes recent only after it actually reaches READY. One tiny
     * preference write per successful tune is negligible and survives restarts. */
    fun push(p: Playable, prefs: SharedPreferences? = null) {
        items.removeAll { it.url == p.url }
        items.add(0, p)
        while (items.size > 8) items.removeAt(items.size - 1)
        prefs?.edit()?.putString(KEY, items.joinToString("\n") { it.url })?.apply()
    }
}

internal object Timeshift {
    // RYZOD_V467_SESSION_OWNERSHIP: a retired writer can only mutate its own
    // counters/ring. One ingest worker and one cleanup worker serve all tunes.
    private class Session(val id: Long, val requestedAt: Long) {
        @Volatile var cancelled = false
        @Volatile var running = true
        @Volatile var preparing = true
        @Volatile var call: okhttp3.Call? = null
        @Volatile var ring: TimeshiftRing? = null
        @Volatile var root: File? = null
        @Volatile var bytesWritten = 0L
        @Volatile var startedAtElapsedMs = 0L
        @Volatile var startedAtWallMs = 0L
        @Volatile var lastByteAt = 0L
        @Volatile var weakGapEvents = 0L
        @Volatile var syncResyncEvents = 0L
        @Volatile var reconnectEvents = 0L
        @Volatile var storageKind: StorageKind? = null
    }

    private val ids = java.util.concurrent.atomic.AtomicLong()
    private val control = Any()
    @Volatile private var current: Session? = null
    private val ingest = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(1),
        java.util.concurrent.ThreadFactory { r -> Thread(r, "ryzod-dvr-ingest").apply { isDaemon = true } },
        java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy()
    )
    private val cleanup = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "ryzod-dvr-cleanup").apply { isDaemon = true }
    }
    // Accessed only by the single ingest worker. Scan abandoned data once per
    // storage root per process, never during a remote/UI callback.
    private val inspectedRoots = HashSet<String>()
    private val liveStreamClient = Net.streamClient.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val bytesWritten: Long get() = current?.bytesWritten ?: 0L
    val active: Boolean get() = current?.let { it.running && !it.cancelled } == true
    val isPreparing: Boolean get() = current?.let { it.preparing && !it.cancelled && it.running } == true
    val file: File? get() = null // compatibility only; no append-only file
    val startedAtElapsedMs: Long get() = current?.startedAtElapsedMs ?: 0L
    val startedAtWallMs: Long get() = current?.startedAtWallMs ?: 0L
    val lastByteAt: Long get() = current?.lastByteAt ?: 0L
    val throughputBps: Double get() = 0.0
    val weakGapEvents: Long get() = current?.weakGapEvents ?: 0L
    val syncResyncEvents: Long get() = current?.syncResyncEvents ?: 0L
    val reconnectEvents: Long get() = current?.reconnectEvents ?: 0L
    val storageKind: StorageKind? get() = current?.storageKind

    fun windowMs(): Long = startedAtElapsedMs.let { start ->
        if (start > 0L) (android.os.SystemClock.elapsedRealtime() - start).coerceAtLeast(0L) else 0L
    }
    fun snapshot(): TimeshiftRing.RingSnapshot? = current?.ring?.snapshot()
    fun oldestVirtualByte(): Long = snapshot()?.oldestVirtualByte ?: bytesWritten
    fun newestVirtualByte(): Long = snapshot()?.newestVirtualByte ?: bytesWritten
    fun generation(): Long = ids.get()
    private fun isCurrent(session: Session): Boolean = current === session && !session.cancelled && session.running

    fun openReader(virtualOffset: Long, expectedGeneration: Long = generation()): TimeshiftRing.RingReader? {
        val session = current ?: return null
        if (session.id != expectedGeneration || !isCurrent(session)) return null
        val reader = runCatching { session.ring?.openReader(virtualOffset) }.getOrNull()
        if (!isCurrent(session)) { reader?.close(); return null }
        return reader
    }

    private fun findTsSync(b: ByteArray, len: Int): Int {
        var i = 0
        while (i + 376 < len) {
            if (b[i] == 0x47.toByte() && b[i + 188] == 0x47.toByte() && b[i + 376] == 0x47.toByte()) return i
            i++
        }
        return -1
    }

    /** Accept a channel change immediately. All storage discovery, probing,
     * directory creation and stale-file cleanup happens on workers. */
    fun start(context: Context, url: String, prefs: SharedPreferences? = null,
              onReady: ((Boolean) -> Unit)? = null): Boolean {
        val session: Session
        val old: Session?
        synchronized(control) {
            old = current
            old?.cancelled = true
            session = Session(ids.incrementAndGet(), android.os.SystemClock.elapsedRealtime())
            current = session
        }
        old?.call?.cancel()
        TimeshiftServer.retireClients(generation())
        val app = context.applicationContext
        ingest.execute { runSession(app, url, session, onReady) }
        return true
    }

    fun stop() {
        val old = synchronized(control) {
            val previous = current
            previous?.cancelled = true
            current = null
            ids.incrementAndGet()
            previous
        }
        old?.call?.cancel()
        TimeshiftServer.retireClients(generation())
        // The one ingest worker exits promptly on cancellation and queues its
        // own cleanup. No UI thread waits for a writer, fsync, or file deletion.
    }

    private fun ready(session: Session, ok: Boolean, callback: ((Boolean) -> Unit)?) {
        session.preparing = false
        if (!ok) session.running = false
        if (callback != null) android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (current === session && !session.cancelled) callback(ok)
        }
    }

    private fun runSession(context: Context, url: String, session: Session, callback: ((Boolean) -> Unit)?) {
        if (!isCurrent(session)) return
        try {
            val target = LiveStorageManager.choose(context)
            if (!isCurrent(session)) return
            if (target == null) {
                StabilityCore.note("dvr_storage_unavailable session=${session.id}")
                ready(session, false, callback)
                return
            }
            if (inspectedRoots.add(target.root.absolutePath)) {
                val abandoned = target.root.listFiles()?.filter {
                    (it.isFile && it.name.startsWith("segment-") && it.name.endsWith(".ts")) ||
                        (it.isDirectory && it.name.startsWith("session-"))
                }.orEmpty()
                cleanup.execute { abandoned.forEach { runCatching { it.deleteRecursively() } } }
            }
            if (!isCurrent(session)) return
            val root = File(target.root, "session-${System.currentTimeMillis()}-${session.id}-${System.nanoTime()}")
            session.root = root
            val historyMs = 30L * 60L * 1000L
            val localRing = TimeshiftRing.open(root, historyMs, target.maxRingBytes)
            session.ring = localRing
            session.storageKind = target.kind
            session.startedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
            session.startedAtWallMs = System.currentTimeMillis()
            session.lastByteAt = session.startedAtWallMs
            if (!isCurrent(session)) return
            TimeshiftServer.ensureStarted()
            if (!isCurrent(session)) return
            if (TimeshiftServer.port == 0) {
                ready(session, false, callback)
                return
            }
            StabilityCore.note("dvr_ring_start session=${session.id} kind=${target.kind} budget=${target.maxRingBytes} setupMs=${android.os.SystemClock.elapsedRealtime() - session.requestedAt}")
            ready(session, true, callback)
            var reconnectDelayMs = 250L
            while (isCurrent(session)) {
                val beforeAttempt = session.bytesWritten
                var storageFailed = false
                try {
                    val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                    val c = liveStreamClient.newCall(req)
                    session.call = c
                    if (!isCurrent(session)) { c.cancel(); break }
                    c.execute().use { resp ->
                        if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                        val inp = resp.body?.byteStream()
                        if (inp != null) {
                            val buf = ByteArray(64 * 1024)
                            val carry = ByteArray(188)
                            var carryLen = 0
                            var aligned = false
                            var pend = java.io.ByteArrayOutputStream()
                            var lastChunkAt = android.os.SystemClock.elapsedRealtime()

                            fun appendToRing(data: ByteArray, off: Int, len: Int) {
                                if (len <= 0) return
                                try {
                                    if (!isCurrent(session)) return
                                    localRing.append(data, off, len)
                                } catch (e: Exception) {
                                    storageFailed = true
                                    throw e
                                }
                                session.bytesWritten += len
                                session.lastByteAt = System.currentTimeMillis()
                            }

                            fun writePackets(data: ByteArray, off0: Int, len0: Int) {
                                // ZAKO_V441_TS_RESYNC: never assume a weak TS feed stays aligned forever.
                                // If one expected packet boundary loses 0x47, stop feeding corrupt framing,
                                // preserve the remaining bytes, and let the existing three-sync detector
                                // re-lock on the next read.
                                var off = off0
                                var len = len0
                                if (carryLen > 0) {
                                    val need = 188 - carryLen
                                    if (len < need) {
                                        System.arraycopy(data, off, carry, carryLen, len)
                                        carryLen += len
                                        return
                                    }
                                    System.arraycopy(data, off, carry, carryLen, need)
                                    if (carry[0] != 0x47.toByte()) {
                                        session.syncResyncEvents++
                                        StabilityCore.note("dvr_ts_sync_lost source=carry count=$session.syncResyncEvents")
                                        aligned = false
                                        pend = java.io.ByteArrayOutputStream()
                                        pend.write(carry, 0, 188)
                                        pend.write(data, off + need, len - need)
                                        carryLen = 0
                                        return
                                    }
                                    appendToRing(carry, 0, 188)
                                    carryLen = 0
                                    off += need
                                    len -= need
                                }
                                val whole = (len / 188) * 188
                                if (whole > 0) {
                                    var badAt = -1
                                    var pos = off
                                    val end = off + whole
                                    while (pos < end) {
                                        if (data[pos] != 0x47.toByte()) {
                                            badAt = pos
                                            break
                                        }
                                        pos += 188
                                    }
                                    if (badAt >= 0) {
                                        val goodLen = badAt - off
                                        if (goodLen > 0) appendToRing(data, off, goodLen)
                                        session.syncResyncEvents++
                                        StabilityCore.note("dvr_ts_sync_lost source=body count=$session.syncResyncEvents")
                                        aligned = false
                                        pend = java.io.ByteArrayOutputStream()
                                        pend.write(data, badAt, (off + len) - badAt)
                                        carryLen = 0
                                        return
                                    }
                                    appendToRing(data, off, whole)
                                }
                                val rem = len - whole
                                if (rem > 0) {
                                    System.arraycopy(data, off + whole, carry, 0, rem)
                                    carryLen = rem
                                }
                            }

                            while (isCurrent(session)) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                if (!isCurrent(session)) break
                                val nowChunkAt = android.os.SystemClock.elapsedRealtime()
                                val inputGapMs = nowChunkAt - lastChunkAt
                                if (inputGapMs >= 750L) {
                                    session.weakGapEvents++
                                    StabilityCore.note("dvr_input_gap ms=$inputGapMs count=$session.weakGapEvents bytes=$session.bytesWritten")
                                }
                                lastChunkAt = nowChunkAt
                                if (!aligned) {
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
                            }

                        }
                    }
                } catch (e: Exception) {
                    if (!isCurrent(session)) break
                    if (storageFailed) {
                        StabilityCore.note("dvr_ring_write_failed session=${session.id} kind=${session.storageKind} type=${e.javaClass.simpleName}")
                        session.running = false
                        break
                    }
                    session.reconnectEvents++
                    StabilityCore.note("dvr_provider_reconnect session=${session.id} count=${session.reconnectEvents} gaps=${session.weakGapEvents} resyncs=${session.syncResyncEvents} type=${e.javaClass.simpleName}")
                } finally {
                    session.call = null
                }
                if (session.bytesWritten > beforeAttempt + 188L * 50L) reconnectDelayMs = 250L
                else reconnectDelayMs = (reconnectDelayMs * 2L).coerceAtMost(2_000L)
                // Short cancellation-aware waits keep the next queued tune from
                // waiting behind an old channel's exponential reconnect delay.
                val until = android.os.SystemClock.elapsedRealtime() + reconnectDelayMs
                while (isCurrent(session) && android.os.SystemClock.elapsedRealtime() < until) Thread.sleep(25)
            }
        } catch (e: Exception) {
            if (isCurrent(session)) {
                StabilityCore.note("dvr_session_failed session=${session.id} type=${e.javaClass.simpleName}")
                ready(session, false, callback)
            }
        } finally {
            session.running = false
            session.preparing = false
            session.call?.cancel()
            session.call = null
            val retiredRing = session.ring
            val retiredRoot = session.root
            cleanup.execute {
                runCatching { retiredRing?.close() }
                runCatching { retiredRoot?.delete() }
            }
        }
    }
}


// ZAKO_V440_RING_SERVER: localhost playback follows the virtual byte timeline
// across physical segment boundaries. Reclaimed history is clamped by RingReader
// and reaching the live tail waits for more bytes instead of rebuilding player.
private object TimeshiftServer {
    @Volatile var port = 0
    @Volatile private var server: java.net.ServerSocket? = null
    private val clients = java.util.concurrent.ConcurrentHashMap<java.net.Socket, Long>()
    private val handlers = java.util.concurrent.ThreadPoolExecutor(
        0, 2, 30L, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.SynchronousQueue<Runnable>(),
        java.util.concurrent.ThreadFactory { r -> Thread(r, "ryzod-dvr-reader").apply { isDaemon = true } }
    )

    @Synchronized
    fun ensureStarted() {
        if (server != null) return
        val latch = java.util.concurrent.CountDownLatch(1)
        Thread {
            try {
                val ss = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
                server = ss
                port = ss.localPort
                latch.countDown()
                while (true) {
                    val sock = try { ss.accept() } catch (_: Exception) { break }
                    val acceptedGeneration = Timeshift.generation()
                    clients[sock] = acceptedGeneration
                    // A tune/stop can race accept and registration. Recheck
                    // after registration so retirement cannot miss this socket.
                    if (acceptedGeneration != Timeshift.generation() || server !== ss) {
                        clients.remove(sock)
                        runCatching { sock.close() }
                        continue
                    }
                    try { handlers.execute { handle(sock, acceptedGeneration) } }
                    catch (_: java.util.concurrent.RejectedExecutionException) {
                        clients.remove(sock)
                        runCatching { sock.close() }
                    }
                }
            } catch (_: Exception) {
                latch.countDown()
            }
        }.apply { isDaemon = true; name = "tshift-ring-server" }.start()
        runCatching { latch.await(2, java.util.concurrent.TimeUnit.SECONDS) }
    }

    fun retireClients(currentGeneration: Long) {
        clients.entries.forEach { (socket, generation) ->
            if (generation != currentGeneration && clients.remove(socket, generation)) {
                runCatching { socket.close() }
            }
        }
    }

    private fun handle(sock: java.net.Socket, acceptedGeneration: Long) {
        try {
            sock.tcpNoDelay = true
            // Incomplete localhost requests must not occupy the bounded reader
            // workers forever. Generation retirement also interrupts writes.
            sock.soTimeout = 2_000
            val request = java.io.BufferedReader(java.io.InputStreamReader(sock.getInputStream()))
            val requestLine = request.readLine() ?: return
            while (true) {
                val line = request.readLine() ?: break
                if (line.isEmpty()) break
            }
            val target = runCatching {
                val path = requestLine.substringAfter(' ').substringBefore(' ')
                val query = path.substringAfter('?', "")
                query.split('&').firstOrNull { it.startsWith("offset=") }
                    ?.substringAfter('=')?.toLongOrNull() ?: 0L
            }.getOrDefault(0L)

            val expectedGeneration = requestLine.substringAfter("generation=", "")
                .substringBefore('&').substringBefore(' ').toLongOrNull() ?: return
            if (expectedGeneration != acceptedGeneration) return
            val ringReader = Timeshift.openReader(target, expectedGeneration) ?: return
            ringReader.use { rr ->
                // Retirement can close the socket during the response header.
                // Acquire and release the reader around that write too.
                val out = java.io.BufferedOutputStream(sock.getOutputStream())
                out.write(
                    ("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: video/mp2t\r\n" +
                        "Cache-Control: no-store\r\n" +
                        "Connection: close\r\n\r\n").toByteArray()
                )
                out.flush()

                val buf = ByteArray(64 * 1024)
                var idleTicks = 0
                while (Timeshift.generation() == expectedGeneration && Timeshift.active) {
                    val n = rr.read(buf)
                    if (n > 0) {
                        idleTicks = 0
                        out.write(buf, 0, n)
                        out.flush()
                    } else if (n == 0 && Timeshift.active) {
                        Thread.sleep(50)
                        if (++idleTicks >= 40) {
                            idleTicks = 0
                            val gone = try {
                                sock.soTimeout = 1
                                sock.getInputStream().read() == -1
                            } catch (_: java.net.SocketTimeoutException) {
                                false
                            } catch (_: Exception) {
                                true
                            }
                            if (gone) break
                        }
                    } else {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Client hung up, channel changed, or storage disappeared.
        } finally {
            clients.remove(sock)
            runCatching { sock.close() }
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        clients.keys.forEach { runCatching { it.close() } }
        clients.clear()
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
    private var liveDvrMediaSources: androidx.media3.exoplayer.source.DefaultMediaSourceFactory? = null
    private var browserVodMediaSources: androidx.media3.exoplayer.source.DefaultMediaSourceFactory? = null
    private var vodUaFallbackUsed = false
    private var skipNativePause = false
    private var pausePreparing = false
    private var pauseResumeRequested = false

    // One coarse check for a video-only stall, including direct/Simple live.
    // Decoder counters track rendered output, not screenshots or screen pixels.
    private val videoHealth = VideoHealthMonitor()
    private val videoHealthHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var videoHealthRunning = false
    private var videoHealthSession = 0L
    private var observedVideoCounters: androidx.media3.exoplayer.DecoderCounters? = null
    private var observedVideoView: PlayerView? = null
    private val videoViews = ArrayList<java.lang.ref.WeakReference<PlayerView>>()

    /** Views share the existing player; registration never prepares a source. */
    fun attachVideoView(view: PlayerView) {
        videoViews.removeAll { it.get() == null }
        if (videoViews.none { it.get() === view }) videoViews.add(java.lang.ref.WeakReference(view))
        if (view.player === player && player?.isPlaying == true) startVideoHealthWatchdog()
    }

    fun detachVideoView(view: PlayerView) {
        videoViews.removeAll { it.get() == null || it.get() === view }
        if (observedVideoView === view) resetVideoObservation()
        if (videoViews.isEmpty()) {
            stopVideoHealthWatchdog()
            // Audio-only live playback has no rendering surface to supervise.
            if (player?.let { it.isPlaying && isAudioOnly(it) } == true) startVideoHealthWatchdog()
        }
    }

    private fun isAudioOnly(p: ExoPlayer): Boolean =
        p.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO) &&
            !p.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)

    private fun visibleVideoView(p: ExoPlayer): PlayerView? {
        videoViews.removeAll { it.get() == null }
        return videoViews.asReversed().firstNotNullOfOrNull { ref ->
            ref.get()?.takeIf { view ->
                val surface = view.videoSurfaceView as? android.view.SurfaceView
                view.player === p && view.isAttachedToWindow && view.isShown &&
                    view.width > 0 && view.height > 0 && surface?.holder?.surface?.isValid == true
            }
        }
    }

    private fun resetVideoObservation() {
        observedVideoCounters = null
        observedVideoView = null
        videoHealth.sample(android.os.SystemClock.elapsedRealtime(), videoHealthSession, false, 0L, 0L)
        noteAudioPlaybackProgress(android.os.SystemClock.elapsedRealtime(), false, 0L)
    }

    private fun noteAudioPlaybackProgress(nowMs: Long, eligible: Boolean, positionMs: Long) {
        if (videoHealth.sampleAudioProgress(nowMs, videoHealthSession, eligible, positionMs)) retriesP = 0
    }

    private val videoHealthTick = object : Runnable {
        override fun run() {
            val p = player
            if (!videoHealthRunning || p == null || !liveMode || backgroundSuspended) {
                stopVideoHealthWatchdog()
                return
            }
            val now = android.os.SystemClock.elapsedRealtime()
            val view = visibleVideoView(p)
            val counters = p.videoDecoderCounters
            if (view !== observedVideoView || counters !== observedVideoCounters) {
                resetVideoObservation()
                observedVideoView = view
                observedVideoCounters = counters
            }
            counters?.ensureUpdated()
            val playingReady = p.isPlaying && p.playbackState == Player.STATE_READY && !pausePreparing
            val eligible = view != null && counters != null && playingReady &&
                p.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
            val recover = videoHealth.sample(now, videoHealthSession, eligible,
                p.currentPosition, counters?.renderedOutputBufferCount?.toLong() ?: 0L)
            if (videoHealth.stableProgressMs >= 15_000L) retriesP = 0
            // An invisible selected-video surface cannot use advancing audio to
            // erase failures. Genuinely audio-only streams earn a fresh budget
            // after sustained READY position progress without needing a surface.
            noteAudioPlaybackProgress(now, playingReady && isAudioOnly(p), p.currentPosition)
            if (recover) {
                val myGen = playbackGen
                StabilityCore.note("video_stall_recover direct=$directLive temporary=${Timeshift.active} mime=${p.videoFormat?.sampleMimeType}")
                // Disable the decoder before rebuilding the same source. Do not
                // recreate the shared player or steal a recording's provider slot.
                if (myGen == playbackGen && player === p && !backgroundSuspended && p.isPlaying) {
                    val wasPlaying = p.playWhenReady
                    p.stop()
                    if (!recoverTemporaryLive()) {
                        zapTo(currentIdxC.intValue, preserveDirect = true)
                    } else p.playWhenReady = wasPlaying && !backgroundSuspended
                }
            }
            if (videoHealthRunning) videoHealthHandler.postDelayed(this, 5_000L)
        }
    }

    private fun startVideoHealthWatchdog() {
        if (videoHealthRunning || backgroundSuspended || !liveMode) return
        if (videoViews.isEmpty() && player?.let { isAudioOnly(it) } != true) return
        videoHealthRunning = true
        videoHealthHandler.postDelayed(videoHealthTick, 5_000L)
    }

    private fun stopVideoHealthWatchdog() {
        videoHealthRunning = false
        videoHealthHandler.removeCallbacks(videoHealthTick)
        resetVideoObservation()
    }

    /** Starts temporary storage only for an explicit Pause or recording request.
     * Stops direct playback before opening the writer: still one provider stream. */
    private fun beginTemporaryLive(keepPlaying: Boolean): Boolean {
        val p = player ?: return false
        val ctx = appContext ?: return false
        val ch = queue.getOrNull(currentIdxC.intValue) ?: return false
        if (!liveMode || !ch.isLive) return false
        if (Timeshift.active) return true
        playbackGen++
        videoHealthSession++
        val myGen = playbackGen
        pausePreparing = true
        pauseResumeRequested = keepPlaying
        simpleRaw = false
        directLive = false
        stopGovernor()
        p.stop()
        p.clearMediaItems()
        p.playWhenReady = keepPlaying && !backgroundSuspended
        Timeshift.start(ctx, tsUrl(ch.url), prefsRef) { ready ->
            if (myGen != playbackGen || player !== p || !liveMode) return@start
            if (!ready) {
                pausePreparing = false
                Timeshift.stop()
                toast(ctx, "Pause recording unavailable: check free storage. Returning to live TV.")
                zapTo(currentIdxC.intValue)
            } else {
                startDvrWhenPrimed(p, ch, myGen, keepPlaying)
            }
        }
        return true
    }

    fun pauseForConfiguration() {
        skipNativePause = true
        player?.pause()
        android.os.Handler(android.os.Looper.getMainLooper()).post { skipNativePause = false }
    }

    fun setPlaying(playing: Boolean) {
        val p = player ?: return
        if (pausePreparing) {
            pauseResumeRequested = playing
            p.playWhenReady = playing
        } else if (liveMode && !playing && !Timeshift.active) {
            beginTemporaryLive(false)
        } else p.playWhenReady = playing
    }

    fun returnToLive(): Boolean {
        if (!liveMode || queue.isEmpty()) return false
        if (Recorder.activeName.value != null) return seekDvrBy(Timeshift.windowMs())
        zapTo(currentIdxC.intValue)
        return true
    }

    fun togglePlaying() {
        setPlaying(if (pausePreparing) !pauseResumeRequested else !(player?.playWhenReady ?: false))
    }

    /** Retry localhost playback without deleting the paused channel history. */
    private fun recoverTemporaryLive(): Boolean {
        if (!liveMode || !Timeshift.active || Timeshift.isPreparing) return false
        val p = player ?: return false
        val ch = queue.getOrNull(currentIdxC.intValue) ?: return false
        val pos = dvrAbsolutePositionMs()
        val offset = (pos * dvrBytesPerMs()).toLong().coerceIn(Timeshift.oldestVirtualByte(), Timeshift.newestVirtualByte())
        // Pending retries belong to the old localhost source, not this reopen.
        playbackGen++
        setGrowingDvrSource(p, ch, offset, pos, p.playWhenReady)
        return true
    }

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
        player?.let { return it }
        simpleRaw = prefs.getBoolean("simple_mode", true)
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
        // ZAKO_V439_SAFE_BUFFER_INVARIANTS: Media3 requires both playback thresholds
        // to be <= minBufferMs. Steady may request a deeper recovery cushion, but
        // the value passed into DefaultLoadControl is clamped before player creation.
        val minBufferMs = (bufferSec * 1000).coerceAtMost(60_000)
        val requestedStartMs = if (steadyRecovery) maxOf(lockMs, 6_000) else lockMs
        val requestedRebufferMs = if (steadyRecovery) maxOf(lockMs * 3, 12_000).coerceAtMost(20_000) else lockMs
        val safeStartMs = minOf(requestedStartMs, minBufferMs)
        val safeRebufferMs = minOf(requestedRebufferMs, minBufferMs)
        StabilityCore.note("v439_buffer_policy steady=$steadyRecovery min=$minBufferMs start=$safeStartMs rebuffer=$safeRebufferMs")
        // ZAKO_V433_BUFFER_CAP: keep the proven startup/rebuffer cushion, but stop
        // VOD or DVR-behind-live from reserving a 60-90s unbounded sample buffer.
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val lowRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 512) <= 256
        val maxBufferMs = (bufferSec * 1000 * 3).coerceIn(30_000, 60_000)
        val loadControl = PlaybackMemoryPolicy.create(
            lowRam = lowRam,
            minBufferMs = minBufferMs,
            maxBufferMs = maxBufferMs,
            startBufferMs = safeStartMs,
            rebufferMs = safeRebufferMs
        )
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
        // DVR Live is a still-growing MPEG-TS stream. Do NOT ask Media3 to
        // discover a fixed duration or do native constant-bitrate seeking here;
        // RYZOD owns the bounded pause-recording window and reopens the localhost stream at
        // already-written packet offsets. The tolerant TS flags help Fire TV lock
        // onto provider streams that begin between keyframes.
        val liveDvrExtractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setTsSubtitleFormats(tsCaptionFormats)
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
            )
        liveDvrMediaSources = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(mediaData, liveDvrExtractors)
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
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (backgroundSuspended && playWhenReady) {
                    p.pause()
                    return
                }
                if (pausePreparing && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    pauseResumeRequested = playWhenReady
                }
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST &&
                    liveMode && !skipNativePause && !backgroundSuspended && !pausePreparing && !Timeshift.active &&
                    p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED) {
                    beginTemporaryLive(false)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playStateC.intValue = playbackState
                if (playbackState == Player.STATE_BUFFERING) noteBufferingStarted()
                if (playbackState == Player.STATE_READY) {
                    // READY can describe an advancing audio clock with a stuck
                    // picture. Live retries reset only after actual video progress.
                    if (!liveMode) retriesP = 0
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
                        if (myGen == playbackGen && liveMode && player === p && !backgroundSuspended) {
                            if (!recoverTemporaryLive()) zapTo(currentIdxC.intValue, preserveDirect = true)
                        }
                    }, 700)
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                resetVideoObservation()
                val fps = p.videoFormat?.frameRate ?: 0f
                if (fps > 0f) videoFpsC.floatValue = fps
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                                                 newPosition: Player.PositionInfo, reason: Int) {
                resetVideoObservation()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && liveMode && !backgroundSuspended) startVideoHealthWatchdog()
                else stopVideoHealthWatchdog()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (backgroundSuspended) return
                StabilityCore.note("playback_error code=${error.errorCode} live=$liveMode type=${error.cause?.javaClass?.simpleName}")
                // ZAKO_V437_LIVE_EDGE_RECOVERY: Media3 documents this as the
                // correct recovery when an HLS/live player falls behind the live window.
                if (liveMode && error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    StabilityCore.note("live_edge_recover")
                    p.seekToDefaultPosition()
                    p.prepare()
                    p.playWhenReady = true
                    return
                }
                if (liveMode && prefsRef?.getBoolean("live_steady_recovery", false) == true) {
                    StabilityCore.note("steady_player_error_safe code=${error.errorCode} direct=$directLive")
                }
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
                    if (myGen != playbackGen || backgroundSuspended) return@postDelayed
                    runCatching {
                        if (liveMode) {
                            // Timeshift trouble? After 3 strikes, flip to direct
                            // provider playback so video ALWAYS works.
                            if (!recoverTemporaryLive()) {
                                noteLiveFail()
                                zapTo(currentIdxC.intValue, preserveDirect = true)
                            }
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
                    val now = System.currentTimeMillis()
                    val cushionMs = p.totalBufferedDuration

                    // ZAKO_V437_NO_SPEED_GOVERNOR: do not manipulate live playback
                    // speed to manufacture a cushion. Keep A/V clocking at 1.0x;
                    // reserve comes from startup/rebuffer thresholds and HLS/direct recovery.
                    val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
                    if (p.playbackParameters.speed != 1.0f) p.setPlaybackSpeed(1.0f)

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
                                    if (!recoverTemporaryLive()) zapTo(currentIdxC.intValue, preserveDirect = true)
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
    // ZAKO_V438_STEADY_CRASH_HOTFIX: Steady must never force a guessed transport
    // or recursively rebuild the player from inside Media3's error callback.
    // Bumped on every channel change / retry / stop. Delayed callbacks
    // capture the value and refuse to run if it has moved on (stale).
    @Volatile private var playbackGen = 0L

    // v4.20 DVR seeking deliberately does NOT ask Media3 to treat the growing
    // TS file like a finished seekable file. We reopen the truthful unknown-
    // length localhost tail at a byte offset that already exists.
    @Volatile private var dvrSourceOffsetBytes = 0L
    @Volatile private var dvrSourceBaseMs = 0L

    private fun dvrBytesPerMs(): Double {
        val ms = Timeshift.windowMs()
        val bytes = Timeshift.bytesWritten
        return if (ms >= 2_000L && bytes >= 188L * 20L) bytes.toDouble() / ms.toDouble() else 0.0
    }

    /** Approximate playhead within RYZOD's own temporary DVR window. */
    fun dvrAbsolutePositionMs(): Long {
        if (!liveMode || simpleRaw || directLive) return player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val window = Timeshift.windowMs()
        val relativeMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        return (dvrSourceBaseMs + relativeMs).coerceIn(0L, window.coerceAtLeast(0L))
    }

    fun canSeekDvr(): Boolean =
        liveMode && !simpleRaw && !directLive && Timeshift.active &&
            Timeshift.windowMs() >= 2_000L && dvrBytesPerMs() > 0.0

    /**
     * Cable-box 30-second DVR jump. The offset is bitrate-estimated and aligned
     * to a 188-byte TS boundary. The writer never restarts and the provider
     * connection never changes; only ExoPlayer reconnects to localhost.
     */
    fun seekDvrBy(deltaMs: Long): Boolean {
        if (!canSeekDvr()) return false
        val p = player ?: return false
        val rate = dvrBytesPerMs()
        if (rate <= 0.0) return false
        val current = dvrAbsolutePositionMs()
        val oldestByte = Timeshift.oldestVirtualByte()
        val newestByte = Timeshift.newestVirtualByte()
        if (newestByte <= oldestByte) return false

        // ZAKO_V440_RING_SEEK_WINDOW: the rewind limit is the oldest segment
        // still retained on USB/internal storage, not a hard-coded 45 minutes.
        // Keep ~500 ms behind the writer tail so Media3 can relock cleanly.
        val liveCushionBytes = (500.0 * rate).toLong().coerceAtLeast(188L)
        val liveSafeByte = (newestByte - liveCushionBytes).coerceAtLeast(oldestByte)
        val requestedByte = ((current + deltaMs).coerceAtLeast(0L) * rate).toLong()
        var offset = requestedByte.coerceIn(oldestByte, liveSafeByte)
        offset = (offset / 188L) * 188L
        if (offset < oldestByte) {
            offset = ((oldestByte + 187L) / 188L) * 188L
            if (offset > liveSafeByte) offset = liveSafeByte
        }
        val targetMs = (offset.toDouble() / rate).toLong().coerceAtLeast(0L)
        reopenDvrAtOffset(offset, targetMs)
        return true
    }

    private fun setGrowingDvrSource(
        p: ExoPlayer,
        ch: Playable,
        offset: Long,
        baseMs: Long,
        keepPlaying: Boolean
    ) {
        dvrSourceOffsetBytes = offset.coerceAtLeast(0L)
        dvrSourceBaseMs = baseMs.coerceAtLeast(0L)
        val uri = Uri.parse(
            "http://127.0.0.1:${TimeshiftServer.port}/live/${System.nanoTime()}?offset=$dvrSourceOffsetBytes&generation=${Timeshift.generation()}"
        )
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
            .build()
        val source = liveDvrMediaSources?.createMediaSource(item)
        if (source != null) p.setMediaSource(source) else p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = keepPlaying && !backgroundSuspended
    }

    /**
     * Media3 1.9 is less forgiving than the old player when a progressive TS
     * connection opens before it has enough bytes to identify PAT/PMT/video.
     * Let the one existing writer bank a small amount first; this costs no extra
     * provider stream and removes the repeated full-screen retry flashes.
     */
    private fun startDvrWhenPrimed(p: ExoPlayer, ch: Playable, myGen: Long, keepPlaying: Boolean) {
        val started = android.os.SystemClock.elapsedRealtime()
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val check = object : Runnable {
            override fun run() {
                if (myGen != playbackGen || player !== p || !liveMode || simpleRaw || directLive) return
                val waited = android.os.SystemClock.elapsedRealtime() - started
                // ZAKO_V441_STEADY_CUSHION: weak-channel mode trades a few seconds of
                // tune latency for enough retained video to ride through short provider stalls.
                val steadyRecovery = prefsRef?.getBoolean("live_steady_recovery", false) == true
                val ready = if (steadyRecovery) {
                    (waited >= 4_000L && Timeshift.bytesWritten >= DVR_PRIME_BYTES) || waited >= 7_000L
                } else {
                    Timeshift.bytesWritten >= DVR_PRIME_BYTES || waited >= 2_000L
                }
                if (ready) {
                    if (steadyRecovery) StabilityCore.note(
                        "steady_prime waited=$waited bytes=${Timeshift.bytesWritten} gaps=${Timeshift.weakGapEvents} resyncs=${Timeshift.syncResyncEvents} reconnects=${Timeshift.reconnectEvents}"
                    )
                    val requestedPlaying = if (pausePreparing) pauseResumeRequested else keepPlaying
                    pausePreparing = false
                    setGrowingDvrSource(p, ch, 0L, 0L, requestedPlaying)
                    startGovernor()
                } else {
                    main.postDelayed(this, 100L)
                }
            }
        }
        main.post(check)
    }

    private fun reopenDvrAtOffset(offset: Long, targetMs: Long) {
        val p = player ?: return
        val ch = queue.getOrNull(currentIdxC.intValue) ?: return
        if (!ch.isLive || simpleRaw || directLive || !Timeshift.active) return
        playbackGen++
        retriesP = 0
        streamDeadC.value = false
        everReadyC.value = false
        val keepPlaying = p.playWhenReady
        // Start a little before the requested wall-clock target. The server also
        // snaps to a nearby PAT packet, giving Fire TV's TS extractor room to
        // relock cleanly after a remote FF/RW jump.
        val prerollMs = 1_500L.coerceAtMost(targetMs)
        val rate = dvrBytesPerMs()
        var safeOffset = offset
        if (rate > 0.0 && prerollMs > 0L) {
            safeOffset = (offset - (prerollMs * rate).toLong()).coerceAtLeast(0L)
            safeOffset = (safeOffset / 188L) * 188L
        }
        setGrowingDvrSource(p, ch, safeOffset, (targetMs - prerollMs).coerceAtLeast(0L), keepPlaying)
    }

    internal fun noteLiveReady() {
        liveFails = 0
        queue.getOrNull(currentIdxC.intValue)?.let { RecentChannels.push(it, prefsRef) }
    }
    internal fun noteLiveFail(): Boolean {
        liveFails++
        // If the DVR writer is still receiving bytes, give the LOCAL playback
        // path more chances before abandoning DVR. v4.21 could silently fall
        // into DIRECT RESCUE after three extractor/reopen errors even though the
        // timeshift file itself was healthy — which disabled rewind/FF while the
        // button still misleadingly said DVR LIVE.
        val writerHealthy = Timeshift.active &&
            Timeshift.bytesWritten > 188L * 50L &&
            (System.currentTimeMillis() - Timeshift.lastByteAt) < 5_000L
        val rescueAfter = if (writerHealthy) 6 else 3
        if (liveFails >= rescueAfter && !directLive) {
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
        if (backgroundSuspended) return
        // A viewer-initiated channel change always gets a fresh attempt at the
        // selected mode. Only an INTERNAL retry is allowed to preserve the
        // current-channel direct-rescue state. This prevents a hidden rescue
        // from following the viewer around and looking like Simple Mode is
        // "stuck on" after the setting was turned off.
        if (!preserveDirect) {
            directLive = false
            // A new viewer-selected channel gets a clean DVR attempt. Failure
            // strikes from the previous channel must never carry over.
            liveFails = 0
            retriesP = 0
            videoHealthSession++
        }
        playbackGen++
        val p = player ?: return
        val ctx = appContext ?: return
        val q = queue
        if (q.isEmpty()) return
        val i = ((idx % q.size) + q.size) % q.size
        val ch = q[i]
        liveProviderReserved = true
        currentIdxC.intValue = i
        everReadyC.value = false
        videoFpsC.floatValue = 0f
        streamDeadC.value = false
        bufferingSince = 0L
        lastBufMs = -1L
        lastBytesSeen = -1L
        lastBytesAt = System.currentTimeMillis()
        lastSpeedChangeAt = 0L
        p.setPlaybackSpeed(1.0f)
        dvrSourceOffsetBytes = 0L
        dvrSourceBaseMs = 0L
        pausePreparing = false
        pauseResumeRequested = false
        stopGovernor()
        Timeshift.stop()
        simpleRaw = prefsRef?.getBoolean("simple_mode", true) ?: true
        directLive = true
        p.stop()
        p.clearMediaItems()
        val item = MediaItem.Builder()
            // Direct playback honors the supplied transport. A playlist URL is
            // not proof that an equivalent raw .ts endpoint exists.
            .setUri(Uri.parse(ch.url))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
            .build()
        p.setMediaItem(item)
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
            stopVideoHealthWatchdog()
            liveProviderReserved = false
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
    fun canTeeRecording(): Boolean = liveMode && !simpleRaw && !directLive && Timeshift.active && (Timeshift.snapshot() != null || Timeshift.isPreparing)

    /** Recording a direct-rescue live channel must return to the one-connection
     * DVR path first; otherwise a tee has no source and a second network stream
     * would violate the provider connection rule. */
    fun prepareCurrentForRecording(): Boolean {
        // A timer may arrive while another app is visible. It owns a silent
        // network writer; never bring a hidden player back to life for a tee.
        if (!liveMode || backgroundSuspended) return false
        if (!canTeeRecording()) beginTemporaryLive(true)
        return Timeshift.active && (Timeshift.snapshot() != null || Timeshift.isPreparing)
    }

    @Volatile private var backgroundSuspended = false
    @Volatile private var liveProviderReserved = false

    /** Stop hidden live playback/DVR work. An active recording is the one
     * intentional exception: it owns the single provider stream while hidden. */
    fun suspendForBackground() {
        backgroundSuspended = true
        pauseResumeRequested = false
        stopGovernor()
        stopVideoHealthWatchdog()
        val p = player ?: return
        p.pause()
        if (Recorder.activeName.value != null) {
            p.pause()
            return
        }
        if (liveMode && queue.isNotEmpty()) {
            pausePreparing = false
            backgroundSuspended = true
            liveProviderReserved = false
            playbackGen++
            stopGovernor()
            Timeshift.stop()
            p.stop()
        } else {
            p.pause()
        }
    }

    internal fun recordingFinished(url:String) {
        if(backgroundSuspended && Recorder.activeName.value==null && currentProviderUrl()==url) suspendForBackground()
    }

    /** Reconnect the remembered live channel when the viewer returns. */
    fun resumeFromBackground() {
        val wasBackground = backgroundSuspended
        backgroundSuspended = false
        val p = player ?: return
        val prefs=prefsRef
        val context=appContext
        val remote=queue.getOrNull(currentIdxC.intValue)?.url?.let { it.startsWith("http://") || it.startsWith("https://") }==true
        if(wasBackground && remote && prefs!=null && context!=null &&
            ProviderStreams.recordingSlots()+ProviderStreams.downloadSlots(context,prefs)+1>ProviderStreams.max(prefs)) {
            // Foreground lifecycle is not permission to steal the recorder's stream.
            p.pause()
            if(!canTeeRecording()) {p.stop();liveProviderReserved=false}
            toast(context,"Recording is using your available provider stream. Stop the recording to watch live TV.")
            return
        }
        if (wasBackground && liveMode && queue.isNotEmpty()) {
            backgroundSuspended = false
            if (Timeshift.active) p.play() else zapTo(currentIdxC.intValue)
        } else if (queue.isNotEmpty()) {
            backgroundSuspended = false
            p.play()
        }
    }

    fun livePathLabel(): String = when {
        Timeshift.active -> "PAUSE RECORDING"
        simpleRaw -> "LIVE • PAUSE AVAILABLE"
        directLive -> "LIVE • PAUSE AVAILABLE"
        liveMode -> "DVR LIVE"
        else -> "ON DEMAND"
    }

    /** User-visible escape from DIRECT RESCUE back to the selected DVR mode.
     *  This restarts only the current live channel/timeshift path; it does not
     *  rebuild the Activity or touch Smooth Live/VOD. */
    fun retryDvrLive(): Boolean {
        if (!liveMode || simpleRaw || queue.isEmpty()) return false
        directLive = false
        liveFails = 0
        retriesP = 0
        streamDeadC.value = false
        startGovernor()
        zapTo(currentIdxC.intValue)
        return true
    }

    /** Provider-connection accounting for the user-selected 1/2/3 stream budget. */
    fun providerConnectionSlots(): Int {
        val cur = queue.getOrNull(currentIdxC.intValue) ?: return 0
        if (cur.url.startsWith("/") || cur.url.startsWith("file:")) return 0
        if (player == null) return 0
        if (liveProviderReserved) return 1
        if (playStateC.intValue == Player.STATE_IDLE) return 0
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
        liveProviderReserved = false
        playbackGen++
        videoHealthSession++
        pausePreparing = false
        stopGovernor()
        stopVideoHealthWatchdog()
        videoViews.clear()
        Timeshift.stop()
        TimeshiftServer.stop()
        runCatching { player?.release() }
        player = null
        queue = emptyList()
        liveMode = false
        directLive = false
        simpleRaw = false
        dvrSourceOffsetBytes = 0L
        dvrSourceBaseMs = 0L
        backgroundSuspended = false
        liveFails = 0
        playStateC.intValue = Player.STATE_IDLE
        streamDeadC.value = false
        everReadyC.value = false
        videoFpsC.floatValue = 0f
        standardMediaSources = null
        tolerantTsMediaSources = null
        liveDvrMediaSources = null
        browserVodMediaSources = null
        vodUaFallbackUsed = false
    }
}

private object BrowseFocusMemory {
    var movieCategory: String? = null
    var movieUrl: String? = null
    var seriesCategory: String? = null
    var seriesId: String? = null
}

/* ----------------------------- movies pane ----------------------------- */

@Composable
private fun VodInfoDialog(
    source: Source?,
    prefs: SharedPreferences,
    movie: Movie,
    onPlay: (Playable) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var info by remember(movie.id) { mutableStateOf<MediaInfo?>(null) }
    var loaded by remember(movie.id) { mutableStateOf(false) }

    LaunchedEffect(movie.id, source) {
        info = try { source?.mediaInfo(movie.id) } catch (_: Exception) { null }
        loaded = true
    }

    val meta = info
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = SurfaceCol,
        title = { Text(movie.name, color = Ink, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    when {
                        !loaded -> "Loading info…"
                        meta?.description?.isNotBlank() == true -> meta.description
                        else -> "No description was supplied by this playlist/provider."
                    },
                    color = Ink, fontSize = 13.sp
                )
                if (loaded && meta != null) {
                    val facts = listOfNotNull(
                        meta.year.takeIf { it.isNotBlank() }?.let { "Year $it" },
                        meta.rating.takeIf { it.isNotBlank() }?.let { "Rating $it" },
                        meta.genre.takeIf { it.isNotBlank() },
                        meta.duration.takeIf { it.isNotBlank() }?.let { "Length $it" }
                    )
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(facts.joinToString("  •  "), color = ProgramCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick={
                    onClose();onPlay(Playable(movie.name,movie.url,isLive=false,artwork=movie.icon))
                }) { Text("▶ PLAY",color=ProgramCyan,fontWeight=FontWeight.Bold) }
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick={
                    toast(context,DownloadStore.start(context,prefs,movie.name,movie.url))
                }) { Text("⬇ DOWNLOAD",color=DownloadGreen,fontWeight=FontWeight.Bold) }
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick=onClose) {
                    Text("CLOSE",color=Ink)
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun CatalogRefreshButton(section:String,refreshing:Boolean,onRefresh:()->Unit) {
    TextButton(onClick=onRefresh,enabled=!refreshing,modifier=Modifier.tvFocus(RoundedCornerShape(10.dp))) {
        Text(if(refreshing) "UPDATING…" else "UPDATE $section",color=Ink,fontWeight=FontWeight.Bold,fontSize=12.sp)
    }
}

@Composable
fun MoviesPane(
    source: Source?,
    prefs: SharedPreferences,
    data: AppData,
    selectedCat: String,
    onPlay: (Playable) -> Unit,
    onLeftToRail: () -> Unit = {},
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
    refreshWarning: String? = null
) {
    var paneHasFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = paneHasFocus) { onLeftToRail() }

    val context = LocalContext.current
    var infoMovie by remember { mutableStateOf<Movie?>(null) }
    infoMovie?.let { movie ->
        VodInfoDialog(
            source = source, prefs = prefs, movie = movie, onPlay = onPlay,
            onClose = { infoMovie = null }
        )
    }

    if (data.movies.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CatalogRefreshButton("MOVIES",refreshing,onRefresh)
            refreshWarning?.let {Text(it,color=Muted,fontSize=11.sp)}
            Text("No movies in this playlist", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("Your provider hasn't included any movies on this login.", fontSize = 13.sp, color = Muted)
        }
        return
    }

    // ZAKO_V433_REMEMBER_FILTERS
    val filtered = remember(data.movies, selectedCat) {
        data.movies.filter { selectedCat == "all" || it.categoryId == selectedCat }
    }
    val restoreUrl = remember(selectedCat) {
        if (BrowseFocusMemory.movieCategory == selectedCat) BrowseFocusMemory.movieUrl else null
    }
    val targetIdx = remember(filtered, restoreUrl) {
        restoreUrl?.let { u -> filtered.indexOfFirst { it.url == u }.takeIf { it >= 0 } } ?: 0
    }
    val targetFocus = remember(selectedCat, restoreUrl) { FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var visibleMovieCount by remember(selectedCat, filtered.size) {
        mutableIntStateOf(minOf(filtered.size, maxOf(BROWSE_PAGE_SIZE * 2, targetIdx + BROWSE_PAGE_SIZE)))
    }
    val visibleMovies = remember(filtered, visibleMovieCount) { filtered.take(visibleMovieCount) }
    LaunchedEffect(gridState, filtered.size) {
        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (last >= visibleMovieCount - 10 && visibleMovieCount < filtered.size) {
                    visibleMovieCount = minOf(filtered.size, visibleMovieCount + BROWSE_PAGE_SIZE)
                }
            }
    }

    LaunchedEffect(selectedCat, filtered.size, restoreUrl) {
        if (BrowseFocusMemory.movieCategory != selectedCat) {
            BrowseFocusMemory.movieCategory = selectedCat
            BrowseFocusMemory.movieUrl = null
        }
        if (filtered.isNotEmpty()) {
            runCatching { gridState.scrollToItem(targetIdx.coerceIn(0, filtered.lastIndex)) }
            kotlinx.coroutines.delay(120)
            runCatching { targetFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().onFocusChanged { paneHasFocus = it.hasFocus }.focusGroup()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onLeftToRail,modifier=Modifier.tvFocus(RoundedCornerShape(10.dp))) {
                Text("← CATEGORIES",color=Accent,fontWeight=FontWeight.Bold)
            }
            CatalogRefreshButton("MOVIES",refreshing,onRefresh)
        }
        refreshWarning?.let {Text(it,color=Muted,fontSize=11.sp,modifier=Modifier.padding(horizontal=14.dp))}

        Text(
            "OK opens details • choose PLAY or DOWNLOAD",
            color = Ink,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 5.dp, bottom = 2.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            gridItemsIndexed(visibleMovies, key = { _, item -> item.url }) { index, m ->
                val target = index == targetIdx
                PosterGridCard(
                    name = (if (WatchStore.isWatched(prefs, m.url)) "✓  " else "") + m.name,
                    icon = m.icon,
                    movie = m, source = source,
                    modifier = Modifier
                        .then(if (target) Modifier.focusRequester(targetFocus) else Modifier)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft && index % 5 == 0) {
                                onLeftToRail(); true
                            } else false
                        },
                    onInfo = { infoMovie = m },
                    onClick = {
                        // ZAKO_V428_MOVIE_DETAILS: X1-style details first.
                        BrowseFocusMemory.movieCategory = selectedCat
                        BrowseFocusMemory.movieUrl = m.url
                        infoMovie = m
                    },
                    onDownload = {
                        BrowseFocusMemory.movieCategory = selectedCat
                        BrowseFocusMemory.movieUrl = m.url
                        toast(context, DownloadStore.start(context, prefs, m.name, m.url))
                    }
                )
            }
        }
    }
}

/* ----------------------------- series pane ----------------------------- */
@Composable
fun SeriesPane(
    source: Source?,
    data: AppData,
    selectedCat: String,
    onSeries: (SeriesItem) -> Unit,
    onLeftToRail: () -> Unit = {},
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
    refreshWarning: String? = null
) {
    var paneHasFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = paneHasFocus) { onLeftToRail() }

    if (source?.supportsSeries != true || data.series.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CatalogRefreshButton("SERIES",refreshing,onRefresh)
            refreshWarning?.let {Text(it,color=Muted,fontSize=11.sp)}
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

    // ZAKO_V433_REMEMBER_FILTERS
    val filtered = remember(data.series, selectedCat) {
        data.series.filter { selectedCat == "all" || it.categoryId == selectedCat }
    }
    val restoreId = remember(selectedCat) {
        if (BrowseFocusMemory.seriesCategory == selectedCat) BrowseFocusMemory.seriesId else null
    }
    val targetIdx = remember(filtered, restoreId) {
        restoreId?.let { id -> filtered.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
    }
    val targetFocus = remember(selectedCat, restoreId) { FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var visibleSeriesCount by remember(selectedCat, filtered.size) {
        mutableIntStateOf(minOf(filtered.size, maxOf(BROWSE_PAGE_SIZE * 2, targetIdx + BROWSE_PAGE_SIZE)))
    }
    val visibleSeries = remember(filtered, visibleSeriesCount) { filtered.take(visibleSeriesCount) }
    LaunchedEffect(gridState, filtered.size) {
        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (last >= visibleSeriesCount - 10 && visibleSeriesCount < filtered.size) {
                    visibleSeriesCount = minOf(filtered.size, visibleSeriesCount + BROWSE_PAGE_SIZE)
                }
            }
    }

    LaunchedEffect(selectedCat, filtered.size, restoreId) {
        if (BrowseFocusMemory.seriesCategory != selectedCat) {
            BrowseFocusMemory.seriesCategory = selectedCat
            BrowseFocusMemory.seriesId = null
        }
        if (filtered.isNotEmpty()) {
            runCatching { gridState.scrollToItem(targetIdx.coerceIn(0, filtered.lastIndex)) }
            kotlinx.coroutines.delay(120)
            runCatching { targetFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().onFocusChanged { paneHasFocus = it.hasFocus }.focusGroup()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onLeftToRail,modifier=Modifier.tvFocus(RoundedCornerShape(10.dp))) {
                Text("← CATEGORIES",color=Accent,fontWeight=FontWeight.Bold)
            }
            CatalogRefreshButton("SERIES",refreshing,onRefresh)
        }
        refreshWarning?.let {Text(it,color=Muted,fontSize=11.sp,modifier=Modifier.padding(horizontal=14.dp))}

        Text(
            "OK opens a series • Hold OK on an episode to download",
            color = Ink,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 5.dp, bottom = 2.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            gridItemsIndexed(visibleSeries, key = { index, item -> "${item.id}:$index" }) { index, item ->
                val target = index == targetIdx
                PosterGridCard(
                    name = item.name,
                    icon = item.icon,
                    modifier = Modifier
                        .then(if (target) Modifier.focusRequester(targetFocus) else Modifier)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft && index % 5 == 0) {
                                onLeftToRail(); true
                            } else false
                        },
                    onClick = {
                        BrowseFocusMemory.seriesCategory = selectedCat
                        BrowseFocusMemory.seriesId = item.id
                        onSeries(item)
                    }
                )
            }
        }
    }
}

/* ----------------------------- settings pane ----------------------------- */

// ZAKO_V426_NATIVE_UPDATER
private const val ZAKO_RELEASE_CERT_SHA256 = "8EF5FE2873F7A9D40302E722822C05B37B471AB08DF23785FA0A1AEB19A2C165"

private suspend fun downloadZakoUpdate(
    context: android.content.Context,
    url: String,
    onProgress: suspend (Long, Long) -> Unit = { _, _ -> }
): java.io.File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val updateDir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
    val target = java.io.File(updateDir, "RYZOD-update.apk")
    val partial = java.io.File(updateDir, "RYZOD-update.apk.part")
    partial.delete()

    val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "RYZOD-Updater/${BuildConfig.VERSION_NAME}")
    }
    try {
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw java.io.IOException("Update server returned ${connection.responseCode}")
        }
        connection.inputStream.use { input ->
            java.io.FileOutputStream(partial).use { output ->
                val expected = connection.contentLengthLong
                val deadline = android.os.SystemClock.elapsedRealtime() + 180_000L
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                var lastProgress = 0L
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (android.os.SystemClock.elapsedRealtime() > deadline) error("Update download timed out")
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                    check(copied <= 128L * 1024 * 1024) { "Update download exceeded expected size" }
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastProgress >= 500) {
                        lastProgress = now
                        onProgress(copied, expected)
                    }
                }
                check(expected <= 0 || copied == expected) { "Update download incomplete" }
            }
        }
    } catch (e: Exception) {
        partial.delete()
        throw e
    } finally {
        connection.disconnect()
    }

    if (partial.length() < 1_000_000L) {
        partial.delete()
        throw java.io.IOException("Downloaded update was unexpectedly small")
    }
    if (target.exists()) target.delete()
    if (!partial.renameTo(target)) {
        partial.copyTo(target, overwrite = true)
        partial.delete()
    }
    target
}

@Suppress("DEPRECATION")
private fun validateZakoUpdateApk(context: android.content.Context, apk: java.io.File) {
    val pm = context.packageManager
    val flags = if (android.os.Build.VERSION.SDK_INT >= 28) {
        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        android.content.pm.PackageManager.GET_SIGNATURES
    }
    val info = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        ?: throw java.io.IOException("Android could not read the update package")

    if (info.packageName != context.packageName) {
        throw java.lang.SecurityException("Update package identity did not match RYZOD")
    }

    val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
    if (versionCode <= BuildConfig.VERSION_CODE.toLong()) {
        throw java.io.IOException("Downloaded package is not newer than this RYZOD version")
    }

    val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) {
        info.signingInfo?.apkContentsSigners ?: emptyArray()
    } else {
        info.signatures ?: emptyArray()
    }
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val trusted = signatures.any { signature ->
        md.digest(signature.toByteArray()).joinToString("") { byte -> "%02X".format(byte) } ==
            ZAKO_RELEASE_CERT_SHA256
    }
    if (!trusted) {
        throw java.lang.SecurityException("Update signature did not match the permanent RYZOD key")
    }
}

private fun canZakoRequestInstall(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

private fun openZakoInstallPermission(context: android.content.Context) {
    val packageUri = android.net.Uri.parse("package:${context.packageName}")
    val specific = android.content.Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        packageUri
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = android.content.Intent(
        android.provider.Settings.ACTION_SECURITY_SETTINGS
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (specific.resolveActivity(context.packageManager) != null) {
            context.startActivity(specific)
        } else {
            context.startActivity(fallback)
        }
    }.onFailure {
        runCatching { context.startActivity(fallback) }
    }
}

private fun launchZakoPackageInstaller(context: android.content.Context, apk: java.io.File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk
    )
    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
        android.content.Intent.FLAG_ACTIVITY_NEW_TASK

    val install = android.content.Intent(android.content.Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(flags)
    }
    runCatching {
        context.startActivity(install)
    }.onFailure {
        val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(flags)
        }
        context.startActivity(fallback)
    }
}

// RYZOD 4.33

/* ZAKO_V449_MANAGED_DVR_SCREEN
 * Recordings screen integration: the existing Recordings view can call this
 * lightweight pane above completed recordings. Manual date/time entry is
 * intentionally independent of provider EPG horizon.
 */
@Composable
fun ManagedDvrSchedulePane(
    prefs: android.content.SharedPreferences,
    channels: List<Playable>,
    refreshToken: Int,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val rows = remember(refreshToken) { ManagedDvrUi.upcoming(prefs) }
    var manual by remember { mutableStateOf<Playable?>(null) }
    manual?.let { ch -> ManualRecordingDialog(prefs,LiveChannel(ch.url,ch.name,null,null,ch.url),onClose={manual=null},onSaved=onRefresh) }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(ManagedDvrUi.upcomingLabel, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (rows.isEmpty()) Text("No future recordings scheduled.", color = Muted, fontSize = 11.sp)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.title, color = Ink, fontWeight = FontWeight.Bold)
                    Text(row.channel + " • " + row.date + " • " + row.start + " – " + row.end + " • " + row.status, color = Muted, fontSize = 10.sp)
                }
                Chip(ManagedDvrUi.editLabel, false) {
                    val existing = ScheduleStore.load(prefs).firstOrNull { it.id == row.id }
                    if (existing != null) {
                        toast(context, ManagedDvrUi.edit(context, prefs, existing.id, existing.title, existing.channelName, existing.url, existing.startMs, existing.endMs))
                        onRefresh()
                    }
                }
                Chip(ManagedDvrUi.cancelLabel, false) {
                    ManagedDvrUi.cancel(context, prefs, row.id)
                    onRefresh()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(ManagedDvrUi.manualLabel, color = Ink, fontWeight = FontWeight.Bold)
        Text("Choose a channel and enter a future start/end time even when the provider guide does not reach that far.", color = Muted, fontSize = 10.sp)
        if (channels.isNotEmpty()) {
            Chip("Manual Recording", false) {
                manual=channels.first()
            }
        }
    }
}

@Composable
fun SettingsPane(prefs: SharedPreferences, onModeChanged: () -> Unit) {
    var bufferSec by remember { mutableIntStateOf(prefs.getInt("buffer_sec", 30)) }
    var autoLast by remember { mutableStateOf(prefs.getBoolean("autoplay_last", false)) }
    val ctx = LocalContext.current
    var updateStatus by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    val updateScope = rememberCoroutineScope()
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
            "Set this to the number of simultaneous connections INCLUDED with your IPTV service — not the number of Fire TV tuners. RYZOD defaults to 1. Recording the channel you are already watching in DVR Live shares that same stream; watching one channel while recording a different channel needs 2. A live stream + different-channel recording + download needs 3.",
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
            "If your service only includes 1 stream, RYZOD will warn/block combinations that need a second connection instead of letting the provider randomly kill one.",
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
            "Device storage: ${if (internalFree >= 0) Storage.gb(internalFree) + " GB free" else "—"}" +
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
                    "A USB drive is plugged in, but Fire OS has not exposed a path RYZOD can prove writable. Use Recheck USB after granting the normal storage permission or reconnecting the drive. RYZOD will never claim USB is active until a real write test passes."
                else ->
                    "Plug in a USB drive or SSD for saved downloads and recordings. Fire OS decides which portable volumes an app may write; RYZOD tests the drive before offering it and falls back safely if the OS blocks it."
            },
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Device", !extOn) {
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
                toast(ctx, if (ok) "USB write test passed." else "USB still isn't writable by RYZOD on this Fire OS setup.")
            }
        }
        Text(
            "New downloads and recordings are written directly to the selected destination — they do not move later. Anything already saved stays where it is. DVR Live keeps a rolling local history while you stay on the channel. RYZOD uses verified USB storage when available and safely falls back to internal storage.",
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
            "How much video RYZOD collects before showing a live channel. Bigger cushion = steadier picture on weak channels, but changing channels takes longer. If certain channels keep re-buffering, bump this up.",
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
            "Matches the Fire TV display to 24/25/30/50/60 fps content when the TV supports it. RYZOD waits until playback is stable and restores normal display preference when you leave the player. The TV may briefly go black while HDMI changes rate.",
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
        Text("Updates", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "RYZOD checks only when you press the button — no background updater taking memory or waking the Fire Stick.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // ZAKO_V453_REMOTE_POLISH
            // ZAKO_V452_CONSOLIDATED
            // ZAKO_V447_FULL_REDESIGN
            // ZAKO_V447_COMPACT_MINI_GUIDE
            // ZAKO_V446_PERSISTENT_UPDATE_BUTTON
            Chip("Check for updates", false) {
                updateStatus = "Checking RYZOD update server…"
                updateUrl = null
                updateScope.launch {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            // ZAKO_V431_FRESH_UPDATE: never reuse stale release metadata.
                            // RYZOD_V458_UPDATER_RELIABILITY
val manifestUrl = "https://raw.githubusercontent.com/lukeypue/Easy-IPTV/main/latest.json?ts=" + System.currentTimeMillis()
val conn = (java.net.URL(manifestUrl).openConnection() as java.net.HttpURLConnection).apply {
    useCaches = false
    connectTimeout = 15_000
    readTimeout = 15_000
    setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
    setRequestProperty("Pragma", "no-cache")
    setRequestProperty("User-Agent", "RYZOD-Updater/" + BuildConfig.VERSION_NAME)
}
val raw = try {
    conn.connect()
    if (conn.responseCode !in 200..299) throw java.io.IOException("Update server returned " + conn.responseCode)
    conn.inputStream.bufferedReader().use { it.readText() }
} finally {
    conn.disconnect()
}
val obj = org.json.JSONObject(raw)
                            check(obj.optInt("versionCode", 0) > 0 && obj.optString("versionName").isNotBlank() &&
                                obj.optString("downloadUrl").startsWith("https://github.com/lukeypue/Easy-IPTV/releases/download/")) {
                                "Update server returned incomplete release information"
                            }
                            Triple(
                                obj.optInt("versionCode", 0),
                                obj.optString("versionName", "new version"),
                                obj.optString("downloadUrl", "")
                            )
                        }
                    }
                    result.onSuccess { (code, name, url) ->
                        if (code > BuildConfig.VERSION_CODE) {
                            updateStatus = "RYZOD $name is available (build $code)."
                            updateUrl = url.takeIf { it.startsWith("http") }
                        } else if (code < BuildConfig.VERSION_CODE) {
                            updateStatus = "Update server lists an older build ($name). Your installed RYZOD ${BuildConfig.VERSION_NAME} is newer."
                        } else {
                            updateStatus = "You're up to date — RYZOD ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})."
                        }
                    }.onFailure {
                        updateStatus = "Update check failed: " + (it.message ?: "network error")
                    }
                }
            }
            if (updateUrl != null) {
                Chip("Get update", false) {
                    val url = updateUrl
                    if (url != null) {
                        if (!canZakoRequestInstall(ctx)) {
                            updateStatus = "Allow RYZOD to install updates, then return and press Get update again."
                            openZakoInstallPermission(ctx)
                        } else {
                            updateStatus = "Downloading update…"
                            updateScope.launch {
                                val result = runCatching {
                                    val apk = downloadZakoUpdate(ctx, url) { bytes, total ->
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            updateStatus = if (total > 0) "Downloading update… ${(bytes * 100 / total).coerceIn(0, 100)}%"
                                                else "Downloading update… ${bytes / (1024 * 1024)} MB"
                                        }
                                    }
                                    validateZakoUpdateApk(ctx, apk)
                                    apk
                                }
                                result.onSuccess { apk ->
                                    updateStatus = "Download complete — confirm Install on the next screen."
                                    launchZakoPackageInstaller(ctx, apk)
                                }.onFailure {
                                    updateStatus = "Couldn't download or verify the update. Check your connection and try again."
                                }
                            }
                        }
                    }
                }
            }
        }
        if (updateStatus.isNotBlank()) {
            Text(updateStatus, color = Accent, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("RYZOD ${BuildConfig.VERSION_NAME} — plays the playlists you provide. This app includes no channels or content of its own.", fontSize = 11.sp, color = Muted)
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
private fun SearchTvKeyboardField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, onSubmitted:()->Unit={}) {
    TvTextField(value, onValueChange, "Search", modifier,
        placeholder = "SEARCH movies, shows, actors, directors & live TV", searchStyle = true, onSubmitted=onSubmitted)
}

private val KeyboardOrange = Color(0xFFD66B14)
private val ChannelOrange = Color(0xFF9C450A)

@Composable
private fun RyzodKeyboardPanel(
    value: String, onValueChange: (String) -> Unit, label: String, password: Boolean,
    compact: Boolean = false, onPrevious: () -> Unit = {}, onNext: () -> Unit = {}, onClose: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    var upper by remember { mutableStateOf(false) }
    var row by remember { mutableIntStateOf(1) }
    var col by remember { mutableIntStateOf(0) }
    val rows = remember(page, upper) { KeyboardLayout.rows(page, upper) }
    val keyFocus = remember { FocusRequester() }
    var confirmPressed by remember {mutableStateOf(false)}
    fun press(key: String) {
        when (key) {
            "abc", "#$%", "áçé" -> {
                page = when(key) { "#$%" -> 1; "áçé" -> 2; else -> 0 }
                row = 0; col = 0
            }
            "SHIFT" -> upper = !upper
            "PREVIOUS" -> onPrevious()
            "NEXT" -> onNext()
            "DONE" -> onClose()
            else -> onValueChange(KeyboardLayout.edit(value, key))
        }
    }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; keyFocus.requestFocus() }
    BackHandler(onBack=onClose)
    Column(Modifier.fillMaxWidth().widthIn(max=620.dp)
        .background(KeyboardOrange.copy(alpha=.82f), RoundedCornerShape(12.dp))
        .border(1.dp, Color(0xFFFFBA65), RoundedCornerShape(12.dp))
        .focusRequester(keyFocus)
        .onPreviewKeyEvent { ev ->
            if(ev.key==Key.DirectionCenter || ev.key==Key.Enter) {
                if(ev.type==KeyEventType.KeyDown) confirmPressed=true
                else if(ev.type==KeyEventType.KeyUp && confirmPressed) {
                    confirmPressed=false
                    press(rows[row][col])
                }
                return@onPreviewKeyEvent true
            }
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val delta = when (ev.key) {
                Key.DirectionLeft -> -1 to 0; Key.DirectionRight -> 1 to 0
                Key.DirectionUp -> 0 to -1; Key.DirectionDown -> 0 to 1
                else -> null
            }
            if (delta != null) {
                val next=KeyboardLayout.move(rows,row,col,delta.first,delta.second)
                row=next.first; col=next.second; true
            } else when(ev.key) {
                Key.DirectionCenter, Key.Enter -> { press(rows[row][col]); true }
                Key.Backspace -> { press("DELETE"); true }
                else -> {
                    val native=ev.nativeKeyEvent
                    if(native.unicodeChar>=32 && !native.isCtrlPressed && !native.isAltPressed) {
                        onValueChange(value+String(Character.toChars(native.unicodeChar))); true
                    } else false
                }
            }
        }.focusable().padding(6.dp), verticalArrangement=Arrangement.spacedBy(3.dp)) {
        if(!compact) Text(label, color=Ink, fontWeight=FontWeight.Bold, fontSize=12.sp)
        if(!compact) Text(if(password) "•".repeat(value.length.coerceAtMost(30)) else value.ifEmpty { "Type here" },
            color=Ink,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
            modifier=Modifier.fillMaxWidth().border(1.dp,Accent,RoundedCornerShape(6.dp)).padding(6.dp))
        rows.forEachIndexed { ri, keys ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                keys.forEachIndexed { ci,key ->
                    val selected=row==ri && col==ci
                    Box(Modifier.weight(if(key=="SPACE") 1.6f else 1f).height(if(compact) 25.dp else 32.dp)
                        .background(if(selected) Color.White else Color(0x66371B08),RoundedCornerShape(4.dp))
                        .border(if(selected) 2.dp else 1.dp,if(selected) Accent else Color.White.copy(alpha=.25f),RoundedCornerShape(4.dp))
                        .focusProperties { canFocus=false }
                        .clickable { row=ri;col=ci;press(key) },contentAlignment=Alignment.Center) {
                        Text(if(key=="SHIFT") "aA" else key, color=if(selected) Color(0xFF331600) else Color.White,
                            fontWeight=FontWeight.Bold,fontSize=if(key.length>3) 10.sp else 14.sp,maxLines=1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RyzodKeyboardDialog(
    value: String,onValueChange:(String)->Unit,label:String,password:Boolean,
    onPrevious:()->Unit={},onNext:()->Unit={},onClose:()->Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest=onClose,
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().padding(10.dp),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=620.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                RyzodKeyboardPanel(value,onValueChange,label,password,onPrevious=onPrevious,onNext=onNext,onClose=onClose)
            }
        }
    }
}


private data class SearchMatches(
    val query: String, val indexIdentity: Any,
    val live: List<LiveChannel>, val movies: List<Movie>, val series: List<SeriesItem>,
    val guide: List<EpgStore.GuideHit>
)

// Each producer below assigns value after cancellable background work. The
// bundled Compose lint detector does not resolve these K2 property assignments;
// first-character, changed-query and remote-Done tests verify their emissions.
@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun SearchTab(
    source: Source?,
    prefs: SharedPreferences,
    data: AppData,
    query: String,
    onQuery: (String) -> Unit,
    onPlay: (Playable) -> Unit,
    onPlayLive: (List<Playable>, Int) -> Unit,
    onSeries: (SeriesItem) -> Unit,
    onDemandWarning: String? = null
) {
    // ZAKO_V440_SEARCH_MOVIE_DETAILS: Search movies use the same details dialog as Movies.
    var searchInfoMovie by remember { mutableStateOf<Movie?>(null) }
    searchInfoMovie?.let { movie ->
        VodInfoDialog(
            source = source, prefs = prefs, movie = movie, onPlay = onPlay,
            onClose = { searchInfoMovie = null }
        )
    }
    var recents by remember { mutableStateOf(loadRecents(prefs)) }
    val resultsFocus=remember {FocusRequester()}
    var resultsFocusRequest by remember {mutableIntStateOf(0)}
    val indexes by androidx.compose.runtime.produceState<Triple<SearchIndex<LiveChannel>, SearchIndex<Movie>, SearchIndex<SeriesItem>>?>(null,data) {
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val task=kotlinx.coroutines.currentCoroutineContext()
            val check={ task.ensureActive() }
            Triple(SearchIndex(data.live,{it.name},checkActive=check),
                SearchIndex(data.movies,{it.name},{it.searchMeta},check),
                SearchIndex(data.series,{it.name},{it.searchMeta},check))
        }
    }

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
            SearchTvKeyboardField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.weight(1f), onSubmitted={resultsFocusRequest++}
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
            "Search titles, actors, directors, genres and live TV — all from one box.",
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
        if (q.isEmpty()) {
            if (recents.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Type a letter to search.", color = Muted, fontSize = 14.sp)
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

        // Catalog results never wait for the guide index to finish.
        val matches by androidx.compose.runtime.produceState<SearchMatches?>(null,q,indexes) {
            val index=indexes ?: return@produceState
            value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val task=kotlinx.coroutines.currentCoroutineContext()
                val check={ task.ensureActive() }
                val matcher=TitleQuery(q)
                SearchMatches(q,index,index.first.find(matcher,checkActive=check),
                    index.second.find(matcher,checkActive=check),index.third.find(matcher,checkActive=check),emptyList())
            }
        }
        val revision=EpgStore.revision.intValue
        val guideIndex by androidx.compose.runtime.produceState<SearchIndex<EpgStore.GuideHit>?>(null,revision) {
            value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val task=kotlinx.coroutines.currentCoroutineContext()
                SearchIndex(EpgStore.searchEntries(),{it.entry.title},checkActive={task.ensureActive()})
            }
        }
        val guideMatches by androidx.compose.runtime.produceState<Triple<String,SearchIndex<EpgStore.GuideHit>,List<EpgStore.GuideHit>>?>(null,q,guideIndex) {
            val index=guideIndex ?: return@produceState
            value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val task=kotlinx.coroutines.currentCoroutineContext()
                val now=System.currentTimeMillis()
                Triple(q,index,index.find(TitleQuery(q),30,{task.ensureActive()},{it.entry.endMs>now}))
            }
        }
        val currentMatches=matches?.takeIf { it.query==q && it.indexIdentity===indexes }
        if(currentMatches==null) {
            Text("Searching…",color=Muted,modifier=Modifier.padding(16.dp))
            return
        }
        val liveHits=currentMatches.live
        val movieHits=currentMatches.movies
        val seriesHits=currentMatches.series
        val guideHits=guideMatches?.takeIf { it.first==q && it.second===guideIndex }?.third.orEmpty()


        // Match guide channels back to playable channels (by guide id, then by name).
        val context = LocalContext.current
        val byEpgId = remember(data) { data.live.filter { it.epgId != null }.associateBy { it.epgId!!.lowercase() } }
        val byNorm = remember(data) {
            data.live.associateBy { it.name.lowercase().replace(Regex("[^a-z0-9]"), "") }
        }
        val guideFmt = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }

        // Search used to launch a live hit as a one-item queue. That made the
        // channel play, but RYZOD no longer knew its real lineup position:
        // channel up/down and the recent-channel mini guide broke (the 24/7
        // Star Wars test exposed it). Always re-enter Live with the complete
        // playlist queue and the searched channel's true index.
        fun playLiveHit(ch: LiveChannel) {
            val fullQueue = data.live.map { livePlayable(prefs, it) }
            val idx = data.live.indexOfFirst { it.url == ch.url }.coerceAtLeast(0)
            onPlayLive(fullQueue, idx)
        }

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

        LaunchedEffect(resultsFocusRequest) {
            if(resultsFocusRequest>0) {
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                runCatching {resultsFocus.requestFocus()}
            }
        }
        LazyColumn(
            modifier=Modifier.focusRequester(resultsFocus).focusGroup(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (liveHits.isNotEmpty()) {
                item { SectionHeader("Live TV") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(liveHits, key = { it.id }) { ch ->
                            SearchLiveCard(ch) { saveRecent(q); playLiveHit(ch) }
                        }
                    }
                }
            }
            if (movieHits.isNotEmpty()) {
                item { SectionHeader("Movies • title / cast / director / genre") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(movieHits) { m ->
                            PosterCard(m.name, m.icon, movie=m, source=source) {
                                saveRecent(q)
                                searchInfoMovie = m
                            }
                        }
                    }
                }
            }
            if (seriesHits.isNotEmpty()) {
                item { SectionHeader("Series • title / cast / director / genre") }
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
                                    playLiveHit(ch)
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
    Text(
        label,
        fontWeight = FontWeight.Black, fontSize = 15.sp, color = ElectricCyan,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

/* ----------------------------- series detail ----------------------------- */
/* ZAKO_V433_EPISODE_DETAILS: readable episode information before playback. */
@Composable
private fun EpisodeInfoDialog(
    seriesName: String,
    season: Int,
    episode: Episode,
    prefs: SharedPreferences,
    onPlay: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val epName = "$seriesName S${season}E${episode.episodeNum}"
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = SurfaceCol,
        title = {
            Column {
                Text(
                    "S${season} • E${episode.episodeNum}",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    episode.title,
                    color = Color(0xFFFFE45C),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(seriesName, color = ProgramCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    episode.plot.ifBlank { "No episode description was supplied by this playlist/provider." },
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(14.dp))
            }
        },
        confirmButton = {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick={onClose();onPlay()}) {
                    Text("▶ PLAY",color=ProgramCyan,fontWeight=FontWeight.Bold,fontSize=14.sp)
                }
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick={
                    toast(context,DownloadStore.start(context,prefs,epName,episode.url))
                }) { Text("⬇ DOWNLOAD",color=DownloadGreen,fontWeight=FontWeight.Bold,fontSize=14.sp) }
                TextButton(modifier=Modifier.tvFocus(RoundedCornerShape(18.dp)),onClick=onClose) {
                    Text("CLOSE",color=Ink,fontWeight=FontWeight.Bold)
                }
            }
        },
        dismissButton = {}
    )
}

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
    var infoEpisode by remember { mutableStateOf<Pair<Int, Episode>?>(null) }
    BackHandler { if (infoEpisode != null) infoEpisode = null else onBack() }

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

    infoEpisode?.let { (season, ep) ->
        EpisodeInfoDialog(
            seriesName = s.name, season = season, episode = ep, prefs = prefs,
            onPlay = {
                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                onPlayQueue(queue, idx)
            },
            onClose = { infoEpisode = null }
        )
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
                            onClick = { infoEpisode = season to ep },
                            onLongAction = {
                                toast(context, DownloadStore.start(context, prefs, epName, ep.url))
                            },
                            trailing = { fm ->
                                IconButton(modifier = fm.then(Modifier.tvFocus(RoundedCornerShape(24.dp))), onClick = {
                                    toast(
                                        context,
                                        DownloadStore.start(context, prefs, epName, ep.url)
                                    )
                                }) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download for offline", tint = DownloadGreen)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class SavedItemAction(
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun SavedItemPopup(title: String, message: String, actions: List<SavedItemAction>, onClose: () -> Unit) {
    val focus = remember(actions.size) { List(actions.size) { FocusRequester() } }
    val enabled = actions.indices.filter { actions[it].enabled }
    val first = enabled.firstOrNull()
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(.94f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceCol,
        title = { Text(title, color = Ink, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = { Text(message, color = Muted, fontSize = 13.sp) },
        confirmButton = {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                actions.forEachIndexed { index, action ->
                    val position = enabled.indexOf(index)
                    TextButton(
                        enabled = action.enabled,
                        modifier = Modifier.focusRequester(focus[index])
                            .focusProperties {
                                if (position >= 0) {
                                    left = focus[enabled[(position + enabled.size - 1) % enabled.size]]
                                    right = focus[enabled[(position + 1) % enabled.size]]
                                }
                            }.tvFocus(RoundedCornerShape(14.dp)),
                        onClick = action.onClick
                    ) {
                        Text(action.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                            color = when {
                                !action.enabled -> Muted.copy(alpha = .45f)
                                action.destructive -> Live
                                action.label == "CLOSE" || action.label == "CANCEL" -> Ink
                                else -> Accent
                            })
                    }
                }
            }
            LaunchedEffect(first) {
                androidx.compose.runtime.withFrameNanos { }
                first?.let { focus[it].requestFocus() }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun ConfirmSavedItemDelete(title: String, kind: String, onCancel: () -> Unit, onDelete: () -> Unit) {
    SavedItemPopup(
        title = "Delete $kind?",
        message = "Are you sure you want to delete this $kind?\n\n$title",
        actions = listOf(
            SavedItemAction("CANCEL", onClick = onCancel),
            SavedItemAction("YES, DELETE", destructive = true, onClick = onDelete)
        ),
        onClose = onCancel
    )
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
    var confirmDownload by remember { mutableStateOf<DownloadStore.Item?>(null) }
    var selectedDownload by remember { mutableStateOf<DownloadStore.Item?>(null) }
    var downloadStates by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        while (true) {
            // If the previous item finished (or a provider slot became free),
            // start the next queued item. This check exists only while the
            // Downloads screen is open; the service itself also chains items.
            DownloadStore.kickQueue(context, prefs)
            // Refresh even on the exact tick a job moves RUNNING → SUCCESS/FAILED;
            // otherwise the last in-flight row can sit visually stuck until the
            // user leaves and re-enters Downloads.
            items = DownloadStore.load(prefs)
            downloadStates = items.associate { it.id to DownloadStore.state(context, it.id) }
            val inFlight = items.filter { d -> DownloadStore.isInFlight(context, d.id) }
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
            kotlinx.coroutines.delay(1_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        val free = remember(items) { DownloadStore.freeBytes(context, prefs) }
        val onDrive = remember { Storage.usingDrive(context, prefs) }
        if (free >= 0) {
            Text(
                (if (onDrive) "External drive: " else "Device storage: ") +
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
                "Saved for offline watching. Add as many titles as you want — RYZOD downloads one at a time in a lightweight queue. Files stay until you delete them."
            else
                "Saved for offline watching. Add as many titles as you want — RYZOD downloads one at a time in a lightweight queue. Files are kept for ${DownloadStore.retentionDays(prefs)} days.",
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
                items(items, key = { it.id }) { d ->
                    val f = File(d.path)
                    val ready = DownloadStore.isReady(context, d)
                    val daysLeft = if (d.expires == Long.MAX_VALUE) Long.MAX_VALUE
                        else ((d.expires - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                    val prog = progressMap[d.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus()
                            .background(SurfaceCol, RoundedCornerShape(14.dp))
                            .clickable { selectedDownload = d }
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
                                val state = DownloadStore.state(context, d.id)
                                val err = DownloadStore.error(context, d.id)
                                val done = prog?.first ?: DownloadStore.progress(context, d.id)?.first ?: 0L
                                val total = prog?.second ?: DownloadStore.progress(context, d.id)?.second ?: -1L
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
                                        state == DownloadStore.STATE_FAILED -> "Download failed${if (err.isNotBlank()) ": $err" else ""}"
                                        state == DownloadStore.STATE_PENDING && !DownloadService.isActive(d.id) -> {
                                            val queued = items.filter { DownloadStore.state(context, it.id) == DownloadStore.STATE_PENDING }
                                            val pos = queued.indexOfFirst { it.id == d.id }.let { if (it >= 0) it + 1 else 1 }
                                            "Queued — #$pos waiting for the current download"
                                        }
                                        pct != null -> "Downloading… $pct%  (${done / 1_048_576} MB of ${total / 1_048_576} MB)$etaText"
                                        done > 0 -> "Downloading… ${done / 1_048_576} MB so far"
                                        else -> "Starting download…"
                                    },
                                    color = if (state == DownloadStore.STATE_FAILED) Live else Muted,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                if (state != DownloadStore.STATE_FAILED) {
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
                        }
                        Text("OPTIONS ›", color = Muted, fontSize = 11.sp)

                    }
                }
            }
        }
    }
    confirmDownload?.let { d ->
        ConfirmSavedItemDelete(d.title, "download", onCancel = { confirmDownload = null }) {
            DownloadStore.stopAndRemove(context, prefs, d)
            items = DownloadStore.load(prefs)
            confirmDownload = null
        }
    }
    selectedDownload?.let { d ->
        val ready = DownloadStore.isReady(context, d)
        val state = downloadStates[d.id] ?: DownloadStore.state(context, d.id)
        val inFlight = state == DownloadStore.STATE_RUNNING || state == DownloadStore.STATE_PENDING
        SavedItemPopup(
            title = d.title,
            message = if (ready) "Saved for offline watching." else "Finish downloading to play. Pause keeps your progress; Resume continues the download.",
            actions = buildList {
                add(SavedItemAction("PLAY", enabled = ready) {
                    selectedDownload = null
                    onPlay(Playable(d.title, d.path, isLive = false))
                })
                if (!ready) {
                    add(SavedItemAction("RESUME", enabled = !inFlight) {
                        toast(context, DownloadStore.resume(context, prefs, d))
                        items = DownloadStore.load(prefs)
                        selectedDownload = null
                    })
                    add(SavedItemAction("PAUSE DOWNLOAD", enabled = inFlight) {
                        toast(context, DownloadStore.pause(context, prefs, d))
                        items = DownloadStore.load(prefs)
                        selectedDownload = null
                    })
                }
                add(SavedItemAction("DELETE", destructive = true) {
                    selectedDownload = null
                    confirmDownload = d
                })
                add(SavedItemAction("CLOSE") { selectedDownload = null })
            },
            onClose = { selectedDownload = null }
        )
    }
}

/* ----------------------------- recordings ----------------------------- */
@Composable
fun RecordingsPane(prefs: SharedPreferences, onPlay: (Playable) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    suspend fun scanRecordings(): List<File> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    var scheds by remember { mutableStateOf(ScheduleStore.load(prefs)) }
    val schedFmt = remember { SimpleDateFormat("EEE, MMM d  h:mm a", Locale.getDefault()) }
    val activeRecording = Recorder.activeName.value
    var confirmRecordingFile by remember { mutableStateOf<File?>(null) }
    var selectedRecordingFile by remember { mutableStateOf<File?>(null) }
    var confirmSchedule by remember { mutableStateOf<ScheduleStore.Sched?>(null) }
    var confirmStopRecording by remember { mutableStateOf(false) }
    LaunchedEffect(activeRecording) {
        // scanRecordings runs on IO; Compose receives only the tiny final file list.
        files = scanRecordings()
        while (Recorder.activeName.value != null) {
            kotlinx.coroutines.delay(5_000)
            files = scanRecordings()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (ProviderStreams.max(prefs) == 1)
                "Provider streams: 1 • Recording the channel you are watching in DVR Live shares that stream. Recording a different channel takes over Live TV."
            else
                "Provider streams: ${ProviderStreams.max(prefs)} • RYZOD may keep Live TV playing while a different channel records, up to your selected IPTV-plan limit.",
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
                (if (onDrive) "External drive: " else "Device storage: ") +
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
                    IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = { confirmSchedule=s }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Cancel", tint = Muted)
                    }
                }
            }
            Text(
                "Keep the device powered on. If it starts late, RYZOD records the remaining time.",
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
                    confirmStopRecording = true
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
                items(files, key = { it.absolutePath }) { f ->
                    val sizeLabel = when {
                        f.length() < 1024 * 1024 -> "${(f.length() / 1024).coerceAtLeast(0)} KB"
                        f.length() < 10L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", f.length() / 1_048_576.0)
                        else -> "${f.length() / (1024 * 1024)} MB"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus()
                            .background(SurfaceCol, RoundedCornerShape(14.dp))
                            .clickable { selectedRecordingFile = f }
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
                        Text("OPTIONS ›", color = Muted, fontSize = 11.sp)

                    }
                }
            }
        }
        selectedRecordingFile?.let { f ->
            SavedItemPopup(
                title = f.nameWithoutExtension.removePrefix("REC_").replace('_', ' '),
                message = "Choose an option for this recording.",
                actions = listOf(
                    SavedItemAction("PLAY") {
                        selectedRecordingFile = null
                        onPlay(Playable(f.nameWithoutExtension, f.absolutePath, isLive = false))
                    },
                    SavedItemAction("DELETE", destructive = true) {
                        selectedRecordingFile = null
                        confirmRecordingFile = f
                    },
                    SavedItemAction("CLOSE") { selectedRecordingFile = null }
                ),
                onClose = { selectedRecordingFile = null }
            )
        }
        confirmStopRecording.takeIf{it}?.let {
            AlertDialog(onDismissRequest={confirmStopRecording=false},containerColor=SurfaceCol,
                title={Text("Are you sure?",color=Ink,fontWeight=FontWeight.ExtraBold)},
                text={Text("Stop the recording now?",color=Muted)},
                confirmButton={TextButton(onClick={Recorder.stop(context);confirmStopRecording=false}){Text("STOP RECORDING",color=Live,fontWeight=FontWeight.Bold)}},
                dismissButton={TextButton(onClick={confirmStopRecording=false}){Text("CLOSE",color=Ink)}})
        }
        confirmSchedule?.let { s ->
            AlertDialog(onDismissRequest={confirmSchedule=null},containerColor=SurfaceCol,
                title={Text("Are you sure?",color=Ink,fontWeight=FontWeight.ExtraBold)},
                text={Text("Delete this scheduled recording?",color=Muted)},
                confirmButton={TextButton(onClick={ScheduleStore.cancel(context,prefs,s.id);scheds=ScheduleStore.load(prefs);confirmSchedule=null}){Text("DELETE",color=Live,fontWeight=FontWeight.Bold)}},
                dismissButton={TextButton(onClick={confirmSchedule=null}){Text("CLOSE",color=Ink)}})
        }
        confirmRecordingFile?.let { f ->
            ConfirmSavedItemDelete(
                f.nameWithoutExtension.removePrefix("REC_").replace('_', ' '),
                "recording", onCancel = { confirmRecordingFile = null }
            ) {
                confirmRecordingFile = null
                scope.launch {
                    val deleted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        !f.exists() || f.delete()
                    }
                    files = scanRecordings()
                    if (!deleted) toast(context, "Couldn't delete the recording. Check that the storage is connected.")
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
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, placeholder: String = "", password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text, searchStyle: Boolean = false, onSubmitted:()->Unit={}
) {
    var editing by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val borderColor = if (searchStyle) Accent else ElectricCyan
    Column(modifier) {
        Column(Modifier.fillMaxWidth().focusRequester(fieldFocus).tvFocus(RoundedCornerShape(12.dp))
            .background(PanelGlow.copy(alpha = .72f), RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { editing = true }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, color = borderColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(when { value.isEmpty() -> placeholder.ifEmpty { "Press OK to type" }
                password -> "•".repeat(value.length.coerceAtMost(24)); else -> value },
                color = if (value.isEmpty()) Muted else Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if(editing && searchStyle) RyzodKeyboardPanel(value,onValueChange,label,password,compact=true,
            onPrevious={editing=false;runCatching { fieldFocus.requestFocus() }},
            onNext={editing=false;runCatching { fieldFocus.requestFocus() };onSubmitted()},
            onClose={editing=false;runCatching { fieldFocus.requestFocus() };onSubmitted()})
    }
    if (editing && !searchStyle) {
        val manager=LocalFocusManager.current
        fun close() { editing=false; runCatching { fieldFocus.requestFocus() } }
        RyzodKeyboardDialog(value,onValueChange,label,password,
            onPrevious={close();manager.moveFocus(FocusDirection.Previous)},
            onNext={close();manager.moveFocus(FocusDirection.Next)},onClose={close()})
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
private fun PosterGridCard(
    name: String,
    icon: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onInfo: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    movie:Movie? = null,
    source:Source? = null
) {
    var longOkFired by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { ev ->
                val ne = ev.nativeKeyEvent
                // ZAKO_V425_REMOTE_INFO: keep poster Left/Right deterministic,
                // but let TV remotes open the visible ⓘ using MENU/INFO.
                val infoKey = ne.keyCode == android.view.KeyEvent.KEYCODE_MENU ||
                    ne.keyCode == android.view.KeyEvent.KEYCODE_INFO
                if (ne.action == android.view.KeyEvent.ACTION_DOWN &&
                    ne.repeatCount == 0 && infoKey && onInfo != null) {
                    onInfo()
                    return@onPreviewKeyEvent true
                }
                if (onDownload == null) return@onPreviewKeyEvent false
                val center = ne.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    ne.keyCode == android.view.KeyEvent.KEYCODE_ENTER
                if (!center) return@onPreviewKeyEvent false
                when {
                    ne.action == android.view.KeyEvent.ACTION_DOWN && ne.repeatCount > 0 && !longOkFired -> {
                        longOkFired = true
                        onDownload()
                        true
                    }
                    ne.action == android.view.KeyEvent.ACTION_UP && longOkFired -> {
                        longOkFired = false
                        true
                    }
                    else -> false
                }
            }
            .tvFocus(RoundedCornerShape(13.dp))
            .clickable { if (!longOkFired) onClick() }
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(11.dp))
                .background(Surface2)
        ) {
            if(movie!=null) {
                MoviePosterImage(movie,source,name,Modifier.fillMaxSize())
            } else if (!icon.isNullOrBlank()) {
                AsyncImage(
                    model = icon,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = Accent, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onDownload != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color(0xDD101217), CircleShape)
                        .focusProperties { canFocus = false }
                        .clickable {
                            // Nested download button: consume this click rather
                            // than launching playback from the poster beneath it.
                            onDownload()
                        }
                        .padding(7.dp)
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download for offline",
                        tint = DownloadGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                name,
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onInfo != null) {
                Text(
                    "ⓘ",
                    color = ProgramCyan, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .focusProperties { canFocus = false }
                        .clickable { onInfo() }
                        .padding(start = 4.dp, end = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PosterCard(name: String, icon: String?, movie:Movie?=null, source:Source?=null, onClick: () -> Unit) {
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
            if(movie!=null) {
                MoviePosterImage(movie,source,name,Modifier.fillMaxSize())
            } else if (!icon.isNullOrBlank()) {
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
    onLongAction: (() -> Unit)? = null,
    trailing: (@Composable (Modifier) -> Unit)? = null
) {
    // Arrow RIGHT from the row lands directly on the trailing button
    // (download / delete) — no long-press gymnastics needed.
    val trailingFocus = remember { FocusRequester() }
    var longOkFired by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { ev ->
                if (onLongAction == null) return@onPreviewKeyEvent false
                val ne = ev.nativeKeyEvent
                val center = ne.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    ne.keyCode == android.view.KeyEvent.KEYCODE_ENTER
                if (!center) return@onPreviewKeyEvent false
                when {
                    ne.action == android.view.KeyEvent.ACTION_DOWN && ne.repeatCount > 0 && !longOkFired -> {
                        longOkFired = true
                        onLongAction()
                        true
                    }
                    ne.action == android.view.KeyEvent.ACTION_UP && longOkFired -> {
                        longOkFired = false
                        true
                    }
                    else -> false
                }
            }
            .focusProperties { if (trailing != null) right = trailingFocus }
            .tvFocus()
            .background(SurfaceCol, RoundedCornerShape(14.dp))
            .clickable { if (!longOkFired) onClick() }
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
    val sbsPrefKey = remember(current.url) { "sbs_2d_${current.url.hashCode()}" }
    var sbs2d by remember(current.url) { mutableStateOf(prefs.getBoolean(sbsPrefKey, false)) }
    var nowNext by remember(current, source) { mutableStateOf<List<EpgEntry>>(emptyList()) }
    // VOD/recordings keep the legacy Media3 control overlay. Live TV has only
    // the RYZOD mini guide; starting this true on live was the reason the old
    // title/gear overlay could still appear underneath the mini guide.
    var overlayVisible by remember { mutableStateOf(queue.getOrNull(start)?.isLive != true) }
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
    val remoteDvrSeek = remember { DvrSeekAccelerator() }
    var dvrSeekHint by remember { mutableStateOf("") }

    fun remoteDvrSeek(dir: Int, event: android.view.KeyEvent?) {
        val step = remoteDvrSeek.next(dir, event?.repeatCount ?: 0)
        dvrSeekHint = DvrSeekAccelerator.label(step.gear, dir)
        if (step.applyNow && !Playback.seekDvrBy(step.deltaMs) && (event?.repeatCount ?: 0) == 0) {
            toast(context, "DVR is still building — pause a moment, then try again.")
        }
    }

    LaunchedEffect(dvrSeekHint) {
        if (dvrSeekHint.isNotEmpty()) {
            val seen = dvrSeekHint
            kotlinx.coroutines.delay(950)
            if (dvrSeekHint == seen) dvrSeekHint = ""
        }
    }

    // ---- X1-style mini guide ----
    // Press OK on a live channel → a slim strip along the bottom shows the
    // last few channels you watched plus the next few in the lineup. Your show
    // keeps playing full-screen the whole time. Arrow to highlight, OK to tune,
    // OK again (or 4s idle) to tuck it away.
    var miniGuideOpen by remember { mutableStateOf(false) }
    // ZAKO_V425_TOUCH_DVR: phones/tablets expose the SAME live DVR engine by touch.
    val hasTouchDvr = remember {
        context.resources.configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH
    }
    var touchDvrOpen by remember { mutableStateOf(false) }
    var touchDvrTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(touchDvrOpen, touchDvrTick) {
        if (touchDvrOpen) {
            val seen = touchDvrTick
            kotlinx.coroutines.delay(6_000)
            if (touchDvrTick == seen) touchDvrOpen = false
        }
    }
    var ccEnabled by remember { mutableStateOf(prefs.getBoolean("cc_enabled", false)) }
    // Whenever the menus appear, park the remote on the show title —
    // OK there closes the menus, arrows reach the other buttons.
    LaunchedEffect(overlayVisible, current.isLive) {
        if (overlayVisible && !current.isLive) {
            // Do NOT force focus onto the Compose title. Media3's progress/play
            // controls own VOD/recording focus; the title is information only.
            kotlinx.coroutines.delay(150)
            pvRef?.showController()
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
            pvRef?.let { Playback.detachVideoView(it); it.player = null }
            if (matchFps) (context as? android.app.Activity)?.let { clearFrameRateMatch(it) }
        }
    }
    // When a DVR recording finishes playing, offer to clean it up — keeps the
    // 16 GB Fire Stick healthy without anyone thinking about storage.
    var askDeleteRecording by remember { mutableStateOf(false) }
    var askDeleteDownload by remember { mutableStateOf(false) }
    LaunchedEffect(playState.intValue, current.url) {
        if (playState.intValue == Player.STATE_ENDED && current.url.startsWith("/")) {
            when {
                current.url.contains("/recordings/") -> askDeleteRecording = true
                current.url.contains("/downloads/") -> askDeleteDownload = true
            }
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
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {
                    askDeleteRecording = false
                    runCatching { File(current.url).delete() }
                    toast(context, "Recording deleted.")
                    onBack(Playback.currentIdxC.intValue, 0L)
                }) { Text("Delete it", color = Accent) }
            },
            dismissButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {
                    askDeleteRecording = false
                    onBack(Playback.currentIdxC.intValue, 0L)
                }) { Text("Keep it", color = Muted) }
            }
        )
    }
    if (askDeleteDownload) {
        AlertDialog(
            onDismissRequest = { askDeleteDownload = false },
            containerColor = SurfaceCol,
            title = { Text("Finished watching", color = Ink) },
            text = {
                Text(
                    "Delete this downloaded video to free up space? (${File(current.url).length() / (1024 * 1024)} MB)",
                    color = Muted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {
                    askDeleteDownload = false
                    DownloadStore.load(prefs).firstOrNull { it.path == current.url }?.let {
                        DownloadStore.remove(prefs, it, context)
                    } ?: runCatching { File(current.url).delete() }
                    toast(context, "Download deleted.")
                    onBack(Playback.currentIdxC.intValue, 0L)
                }) { Text("Delete it", color = Accent) }
            },
            dismissButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = {
                    askDeleteDownload = false
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
            toast(context, "That would need a second provider stream while recording. Your RYZOD setting is 1 stream — stay on this channel or stop recording.")
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
        PlayerKeys.handler = { key, event ->
            when (key) {
                // Fire TV's Menu button is a natural cable-box CC shortcut.
                // It only flips Media3 text-track selection; no extra decoder or
                // background worker is created.
                android.view.KeyEvent.KEYCODE_MENU -> {
                    setCcEnabled(!ccEnabled)
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    Playback.togglePlaying(); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { remoteDvrSeek.reset(); Playback.setPlaying(true); true }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    Playback.setPlaying(false); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    val liveNow = queue.getOrNull(Playback.currentIdxC.intValue)?.isLive == true
                    if (liveNow) {
                        if (!Playback.simpleRaw && !Playback.directLive) remoteDvrSeek(+1, event)
                    } else exo.seekForward()
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    val liveNow = queue.getOrNull(Playback.currentIdxC.intValue)?.isLive == true
                    if (liveNow) {
                        if (!Playback.simpleRaw && !Playback.directLive) remoteDvrSeek(-1, event)
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
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (miniGuideOpen) false
                    else if (current.isLive) {
                        pvRef?.hideController()
                        overlayVisible = false
                        miniGuideOpen = true
                        true
                    } else { pvRef?.showController(); true }
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
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

    LaunchedEffect(current, source, EpgStore.loaded.value) {
        nowNext = emptyList()
        if (!current.isLive) return@LaunchedEffect
        val fromGuide = EpgStore.guide(current.guideKey, current.name)
        if (fromGuide.isNotEmpty()) {
            nowNext = fromGuide.take(2)
        } else {
            val id = current.epgId
            if (id != null && source != null && source.supportsEpg) {
                // Local guide results stay immediate. A brief settle delay avoids
                // provider guide requests for channels passed during rapid zapping.
                kotlinx.coroutines.delay(250)
                nowNext = source.epg(id, 2)
            }
        }
    }

    val recordingThis = Recorder.activeName.value.let { a ->
        a != null && (a == current.name || a.endsWith("(${current.name})"))
    }

    fun toggleSbs2d() {
        sbs2d = !sbs2d
        prefs.edit().putBoolean(sbsPrefKey, sbs2d).apply()
        toast(
            context,
            if (sbs2d) "3D side-by-side correction on for this channel."
            else "3D side-by-side correction off."
        )
    }

    fun cyclePictureSize() {
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
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
                    Playback.attachVideoView(this)
                    // Live TV has ONE controller: RYZOD's cable-box mini guide.
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
                            if (vis == android.view.View.VISIBLE && !current.isLive) {
                                pv.post { wireStockPlayerDpad(pv) }
                            } else if (vis != android.view.View.VISIBLE) {
                                pv.post { pv.requestFocus() }
                            }
                        }
                    )

                    // Fire TV focus bridge for VOD/recordings. Media3's stock
                    // controller can move UP from the seek bar but on some Fire
                    // OS builds spatial focus will not move DOWN again. Explicitly
                    // bridge the main play row <-> progress bar so the viewer can
                    // always get back to the bottom timeline.
                    if (!current.isLive) {
                        // v4.22: wire the actual Android View focus graph instead
                        // of hoping the PlayerView parent receives child key
                        // events. This fixes the Fire OS trap where UP reaches
                        // Play/FF/RW/title but DOWN can never return to the bar.
                        post { wireStockPlayerDpad(this) }
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action != android.view.KeyEvent.ACTION_DOWN) {
                                false
                            } else {
                                val progress = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress)
                                val play = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play_pause)
                                when (keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (progress != null && !progress.hasFocus()) {
                                            progress.requestFocus()
                                            true
                                        } else false
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (progress?.hasFocus() == true && play != null) {
                                            play.requestFocus()
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            }
                        }
                    }
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
                pvRef = view
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
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
        )
        if (current.isLive && hasTouchDvr && !miniGuideOpen) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(current.url) {
                        detectTapGestures {
                            touchDvrOpen = !touchDvrOpen
                            touchDvrTick = System.currentTimeMillis()
                        }
                    }
            )
        }
        if (current.isLive && hasTouchDvr && touchDvrOpen && !miniGuideOpen) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .background(Color(0xE6171922), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniGuideControl("⏪ REW") {
                    touchDvrTick = System.currentTimeMillis()
                    if (!Playback.seekDvrBy(-30_000L)) toast(context, "Rewind needs DVR Live and a little recorded history.")
                }
                MiniGuideControl(if (exo.isPlaying) "❚❚ PAUSE" else "▶ PLAY") {
                    touchDvrTick = System.currentTimeMillis()
                    Playback.togglePlaying()
                }
                MiniGuideControl("FF ⏩") {
                    touchDvrTick = System.currentTimeMillis()
                    if (!Playback.seekDvrBy(30_000L)) toast(context, "Fast forward needs DVR Live.")
                }
                MiniGuideControl("LIVE", activeColor = ProgramCyan) {
                    touchDvrTick = System.currentTimeMillis()
                    if (!Playback.returnToLive()) toast(context, "Already live, or DVR Live is not active.")
                }
                MiniGuideControl("● REC", enabled = current.canRecord, activeColor = Live) {
                    touchDvrTick = System.currentTimeMillis()
                    if (!current.canRecord) toast(context, "Recording is not available for this channel.")
                    else showRecordChoice = true
                }
            }
        }

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
                    sbs2d = sbs2d,
                    onToggleCc = { setCcEnabled(!ccEnabled) },
                    onToggleSbs = { toggleSbs2d() },
                    afrEnabled = matchFps,
                    recordingThis = recordingThis,
                    canRecord = current.canRecord,
                    onToggleAfr = {
                        matchFps = !matchFps
                        prefs.edit().putBoolean("match_fps_live", matchFps).apply()
                        if (!matchFps) (context as? android.app.Activity)?.let { clearFrameRateMatch(it) }
                    },
                    onRecord = {
                        if (recordingThis) {
                            Recorder.stop(context)
                            toast(context, "Recording saved — find it in Recordings.")
                        } else if (Recorder.activeName.value != null) {
                            toast(context, "RYZOD is already recording ${Recorder.activeName.value}. Stop that recording first.")
                        } else if (Playback.simpleRaw) {
                            toast(context, "Recording needs DVR Live. Switch to DVR Live, let the picture lock in, then press REC.")
                        } else {
                            showRecordChoice = true
                        }
                    },
                    onResize = { cyclePictureSize() },
                    onToggleLiveMode = {
                        when {
                            Playback.directLive -> {
                                if (Playback.retryDvrLive()) {
                                    toast(context, "Retrying DVR Live…")
                                }
                            }
                            else -> {
                                val nextSmooth = !Playback.simpleRaw
                                if (nextSmooth && Recorder.activeName.value != null) {
                                    toast(context, "Stop recording before switching to Smooth Live.")
                                } else {
                                    Playback.setSmoothLive(nextSmooth, context)
                                }
                                if (!nextSmooth && !Storage.usingDrive(context, prefs)) {
                                    toast(context, "DVR Live is on. A verified USB drive is recommended for long pause/rewind/recording sessions.")
                                }
                            }
                        }
                        miniGuideOpen = false
                    },
                onTune = { ch ->
                    if (Recorder.activeName.value != null && ch.url != current.url && ProviderStreams.max(prefs) < 2) {
                        toast(context, "Changing channels while recording needs 2 provider streams. Your RYZOD setting is 1.")
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
        if (dvrSeekHint.isNotEmpty()) {
            Text(
                dvrSeekHint,
                color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 26.dp)
                    .background(Color(0xCC171922), RoundedCornerShape(10.dp))
                    .border(2.dp, FocusPink, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
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
        if (overlayVisible && !current.isLive) Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                        pvRef?.showController()
                        pvRef?.post {
                            pvRef?.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play_pause)
                                ?.requestFocus()
                        }
                        true
                    } else false
                }
            ) {
                IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                    onBack(Playback.currentIdxC.intValue, exo.currentPosition.coerceAtLeast(0L))
                }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(
                    Modifier
                        .weight(1f)
                        // Title is information only. Making it a separate Compose
                        // focus target trapped the Fire remote above Media3's
                        // play/progress controls.
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
                    onClick = { cyclePictureSize() }
                ) {
                    Icon(
                        Icons.Filled.AspectRatio,
                        contentDescription = "Screen shape: fit, stretch, or zoom",
                        tint = Color.White
                    )
                }
                if (current.canRecord) {
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
private fun MiniGuideNowInfoDialog(
    channelName: String,
    nowShow: EpgEntry,
    fmt: SimpleDateFormat,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = SurfaceCol,
        title = {
            Text(
                nowShow.title,
                color = Color(0xFFFFE45C),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(channelName, color = ProgramCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    "${fmt.format(Date(nowShow.startMs))}–${fmt.format(Date(nowShow.endMs))}",
                    color = Ink,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    nowShow.desc.ifBlank { "No description was supplied by the guide." },
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(18.dp)), onClick = onClose) {
                Text("CLOSE", color = ProgramCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ZAKO_V445_RESTORED_FULL_CHAIN
@Composable
private fun MiniGuide(
    queue: List<Playable>,
    currentIdx: Int,
    recent: List<Playable>,
    nowNext: List<EpgEntry>,
    fmt: SimpleDateFormat,
    prefs: SharedPreferences,
    ccEnabled: Boolean,
    sbs2d: Boolean,
    onToggleCc: () -> Unit,
    onToggleSbs: () -> Unit,
    afrEnabled: Boolean,
    recordingThis: Boolean,
    canRecord: Boolean,
    onToggleAfr: () -> Unit,
    onRecord: () -> Unit,
    onResize: () -> Unit,
    onToggleLiveMode: () -> Unit,
    onTune: (Playable) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    // ZAKO_V432_MINI_ACCESSIBILITY: larger three-row guide for across-room reading.
    val context = LocalContext.current
    val player = Playback.player
    val current = queue.getOrNull(currentIdx)
    val nowMs = System.currentTimeMillis()
    val nowShow = nowNext.firstOrNull { nowMs in it.startMs until it.endMs } ?: nowNext.firstOrNull()
    val nextShow = nowShow?.let { ns -> nowNext.getOrNull(nowNext.indexOf(ns) + 1) }
        ?: nowNext.firstOrNull { it.startMs > nowMs }

    // Previous means PREVIOUSLY WATCHED — never nearby lineup filler. This is the
    // cable-box behavior people expect when they want to jump back quickly.
    val entries = remember(recent, current?.url) {
        recent.filter { it.url != current?.url }.take(8)
    }

    var showRecent by remember { mutableStateOf(false) }
    var showNowInfo by remember { mutableStateOf(false) }
    if (showNowInfo && nowShow != null) {
        // ZAKO_V434_MINI_INFO: current-show information is readable without leaving Live TV.
        MiniGuideNowInfoDialog(
            channelName = current?.name.orEmpty(),
            nowShow = nowShow,
            fmt = fmt,
            onClose = { showNowInfo = false }
        )
    }
    var playerBufferMs by remember { mutableLongStateOf(0L) }
    var playerPosMs by remember { mutableLongStateOf(0L) }
    var dvrWindowMs by remember { mutableLongStateOf(0L) }
    var dvrBytes by remember { mutableLongStateOf(0L) }
    var displayHz by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }
    val timelineSeek = remember { DvrSeekAccelerator() }
    val lineupFocus = remember { FocusRequester() }
    val lineupState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIdx - 1).coerceAtLeast(0)
    )

    LaunchedEffect(currentIdx, queue.size) {
        if (queue.isNotEmpty()) {
            lineupState.scrollToItem((currentIdx - 1).coerceIn(0, queue.lastIndex))
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            playerBufferMs = player?.totalBufferedDuration ?: 0L
            playerPosMs = Playback.dvrAbsolutePositionMs()
            dvrWindowMs = Timeshift.windowMs()
            dvrBytes = Timeshift.bytesWritten
            isPlaying = player?.isPlaying == true
            displayHz = runCatching {
                (context as? android.app.Activity)?.windowManager?.defaultDisplay?.mode?.refreshRate ?: 0f
            }.getOrDefault(0f)
            kotlinx.coroutines.delay(500)
        }
    }

    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastTouch, isPlaying, showRecent) {
        if (isPlaying) {
            val timeout = if (showRecent) 20_000L else 12_000L
            kotlinx.coroutines.delay(timeout)
            if (isPlaying && System.currentTimeMillis() - lastTouch >= timeout) onClose()
        }
    }

    val playFocus = remember { FocusRequester() }
    val recFocus = remember { FocusRequester() }
    val ccFocus = remember { FocusRequester() }
    val modeFocus = remember { FocusRequester() }
    val sizeFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val sbsFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val recentFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { playFocus.requestFocus() }
    }
    LaunchedEffect(showRecent) {
        if (showRecent && entries.isNotEmpty()) {
            kotlinx.coroutines.delay(80)
            runCatching { recentFocus.requestFocus() }
        }
    }
    BackHandler(enabled = showRecent) {
        showRecent = false
        lastTouch = System.currentTimeMillis()
        runCatching { previousFocus.requestFocus() }
    }
    BackHandler(enabled = showNowInfo) {
        showNowInfo = false
        lastTouch = System.currentTimeMillis()
    }

    fun touch() { lastTouch = System.currentTimeMillis() }

    fun seekDvr(deltaMs: Long, quiet: Boolean = false) {
        touch()
        when {
            Playback.simpleRaw -> if (!quiet) toast(context, "Rewind needs DVR Live.")
            Playback.directLive -> if (!quiet) toast(context, "DVR is in Direct Rescue. Select DVR RETRY first.")
            current?.isLive != true -> if (!quiet) toast(context, "DVR controls are only for Live TV.")
            !Playback.seekDvrBy(deltaMs) -> if (!quiet)
                toast(context, "DVR is still building — pause a moment, then try again.")
        }
    }

    fun acceleratedTimelineSeek(dir: Int, repeatCount: Int) {
        val step = timelineSeek.next(dir, repeatCount)
        if (step.applyNow) seekDvr(step.deltaMs, quiet = repeatCount > 0)
    }

    val sourceFps = Playback.videoFpsC.floatValue
    val dvrActive = current?.isLive == true &&
        !Playback.simpleRaw && !Playback.directLive &&
        Timeshift.snapshot() != null && Timeshift.active
    val dvrProgress = if (dvrWindowMs > 0L)
        (playerPosMs.toFloat() / dvrWindowMs.toFloat()).coerceIn(0f, 1f)
    else 1f
    val behindMs = (dvrWindowMs - playerPosMs).coerceAtLeast(0L)

    fun shortTime(ms: Long): String {
        val sec = (ms / 1000L).coerceAtLeast(0L)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val ss = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
    }

    val modeLabel = when {
        Playback.simpleRaw -> "SMOOTH"
        Playback.directLive -> "DVR RETRY"
        else -> "DVR LIVE"
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
                    listOf(Color(0x12000000), Color(0xF0000000))
                )
            )
            .padding(top = 8.dp, bottom = 9.dp, start = 14.dp, end = 14.dp)
    ) {
        // ZAKO_V434_MINI_HEADER: keep channel identity on the left and give the
        // current program its own readable space on the right instead of stacking
        // the title underneath the channel name.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (current != null) {
                ChannelIcon(current.name, current.artwork, 32.dp)
                Spacer(Modifier.width(8.dp))
            }
            Row(
                modifier = Modifier.then(Modifier.width(210.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    current?.name ?: "",
                    color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (recordingThis) {
                    Spacer(Modifier.width(7.dp))
                    Text("● REC", color = Live, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            if (nowShow != null) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nowShow.title,
                        color = Color(0xFFFFE45C), // ZAKO_V434_MINI_NOW_YELLOW
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${fmt.format(Date(nowShow.startMs))}–${fmt.format(Date(nowShow.endMs))}" +
                            (if (nextShow != null) "   •   Next ${fmt.format(Date(nextShow.startMs))}: ${nextShow.title}" else ""),
                        color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                buildString {
                    append(Playback.livePathLabel())
                    if (afrEnabled) {
                        append("  •  AFR ")
                        if (sourceFps > 0f && displayHz > 0f) {
                            append(String.format(java.util.Locale.US, "%.2f→%.2f", sourceFps, displayHz))
                        } else append("ON")
                    }
                    append("  •  ${ProviderStreams.max(prefs)} streams")
                },
                color = if (Playback.directLive) Accent else Muted,
                fontSize = 8.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(5.dp))

        // One small row is the whole Live-TV control surface. Physical FF/RW
        // works globally, so we do not waste screen space on dead 30-second boxes.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniGuideControl(
                if (isPlaying) "❚❚ PAUSE" else "▶ PLAY",
                modifier = Modifier.weight(1f).focusRequester(playFocus).focusProperties {
                    left = settingsFocus; right = recFocus; down = timelineFocus
                }
            ) {
                touch()
                Playback.togglePlaying()
            }

            MiniGuideControl(
                if (recordingThis) "■ STOP REC" else "● REC",
                enabled = canRecord,
                activeColor = Live,
                modifier = Modifier.weight(1f).focusRequester(recFocus).focusProperties {
                    left = playFocus; right = ccFocus; down = timelineFocus
                }
            ) {
                touch()
                if (canRecord) onRecord() else toast(context, "Recording is not available for this channel.")
            }

            MiniGuideControl(
                if (ccEnabled) "CC ON" else "CC",
                modifier = Modifier.weight(0.72f).focusRequester(ccFocus).focusProperties {
                    left = recFocus; right = modeFocus; down = timelineFocus
                }
            ) { touch(); onToggleCc() }

            MiniGuideControl(
                modeLabel,
                modifier = Modifier.weight(1f).focusRequester(modeFocus).focusProperties {
                    left = ccFocus; right = sizeFocus; down = timelineFocus
                },
                activeColor = if (Playback.directLive) Accent else Ink
            ) { touch(); onToggleLiveMode() }

            MiniGuideControl(
                "SIZE",
                modifier = Modifier.weight(0.72f).focusRequester(sizeFocus).focusProperties {
                    left = modeFocus; right = infoFocus; down = timelineFocus
                }
            ) { touch(); onResize() }

            MiniGuideControl(
                "ⓘ INFO",
                enabled = nowShow != null,
                activeColor = Color(0xFFFFE45C),
                modifier = Modifier.weight(0.82f).focusRequester(infoFocus).focusProperties {
                    left = sizeFocus; right = sbsFocus; down = timelineFocus
                }
            ) {
                touch()
                if (nowShow != null) showNowInfo = true
                else toast(context, "Program information is not available right now.")
            }

            MiniGuideControl(
                if (sbs2d) "3D→2D" else "3D",
                modifier = Modifier.weight(0.72f).focusRequester(sbsFocus).focusProperties {
                    left = infoFocus; right = previousFocus; down = timelineFocus
                },
                activeColor = if (sbs2d) Accent else Ink
            ) { touch(); onToggleSbs() }

            MiniGuideControl(
                "PREVIOUS",
                modifier = Modifier
                    .weight(0.92f)
                    .focusRequester(previousFocus)
                    .focusProperties { left = sbsFocus; right = settingsFocus; down = timelineFocus }
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown && entries.isNotEmpty()) {
                            showRecent = true; touch(); true
                        } else false
                    }
            ) {
                touch()
                if (entries.isEmpty()) toast(context, "Watch a few channels first — they'll appear here.")
                else showRecent = !showRecent
            }

            MiniGuideControl(
                "⚙",
                modifier = Modifier.weight(0.55f).focusRequester(settingsFocus).focusProperties {
                    left = previousFocus; right = playFocus; down = timelineFocus
                }
            ) { touch(); onOpenSettings() }
        }

        Spacer(Modifier.height(5.dp))

        // X1-inspired program/timeshift line. The line spans the whole scheduled
        // program. Gold = show progress, cyan = the part RYZOD has actually stored,
        // pink dot = current playhead. Left/right on THIS line seeks, while the
        // remote's physical FF/RW keys do the same from anywhere on screen.
        // RYZOD_V462_CHANNEL_CLOCK_DVR
        // Temporary DVR belongs to the channel, never to an EPG program.
        val trueDvrStartWall = Timeshift.startedAtWallMs
        val availableMs = minOf(dvrWindowMs, 55L * 60L * 1000L).coerceAtLeast(0L)
        val visibleDvrStartWall = if (trueDvrStartWall > 0L) (nowMs - availableMs).coerceAtLeast(trueDvrStartWall) else 0L
        val showStart = visibleDvrStartWall
        val playInVisibleMs = (playerPosMs - (dvrWindowMs - availableMs).coerceAtLeast(0L)).coerceIn(0L, availableMs.coerceAtLeast(1L))
        val programLiveFraction = 1f
        val programPlayFraction = if (availableMs > 0L) (playInVisibleMs.toFloat()/availableMs.toFloat()).coerceIn(0f,1f) else 1f
        val dvrStartFraction = 0f
        val dvrEndFraction = if (dvrActive) 1f else 0f
        val hasProgramWindow = false
        val showEnd = nowMs

        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .tvFocus(RoundedCornerShape(8.dp))
                .focusRequester(timelineFocus)
                .focusProperties {
                    up = playFocus
                    if (queue.size > 1) down = lineupFocus
                    else if (showRecent && entries.isNotEmpty()) down = recentFocus
                }
                .focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionLeft -> {
                            acceleratedTimelineSeek(-1, ev.nativeKeyEvent.repeatCount)
                            true
                        }
                        Key.DirectionRight -> {
                            acceleratedTimelineSeek(+1, ev.nativeKeyEvent.repeatCount)
                            true
                        }
                        Key.DirectionDown -> false
                        else -> false
                    }
                }
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (hasProgramWindow) fmt.format(Date(showStart))
                        else if (visibleDvrStartWall > 0L) fmt.format(Date(visibleDvrStartWall)) else "START",
                        color = Ink, fontSize = 9.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(if (hasProgramWindow) fmt.format(Date(showEnd)) else "LIVE", color = Ink, fontSize = 9.sp)
                }
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val y = size.height / 2f
                    val w = size.width
                    drawLine(Color(0x55454558), Offset(0f, y), Offset(w, y), 7f, StrokeCap.Round)
                    // Full program track in a quiet gold, elapsed part brighter.
                    drawLine(Accent.copy(alpha = 0.28f), Offset(0f, y), Offset(w, y), 7f, StrokeCap.Round)
                    drawLine(Accent, Offset(0f, y), Offset(w * programLiveFraction, y), 7f, StrokeCap.Round)
                    if (dvrActive && dvrEndFraction > dvrStartFraction) {
                        drawLine(Color(0xFF33E1FF), Offset(w * dvrStartFraction, y), Offset(w * dvrEndFraction, y), 5f, StrokeCap.Round)
                    }
                    drawCircle(FocusPink, 6f, Offset(w * programPlayFraction, y))
                }
                val status = when {
                    Playback.directLive -> "DVR unavailable — choose DVR RETRY above"
                    Playback.simpleRaw -> "Smooth Live • ${String.format(java.util.Locale.US, "%.1f", playerBufferMs / 1000.0)}s player buffer"
                    dvrActive -> {
                        val available = minOf(dvrWindowMs, Timeshift.windowMs())
                        "DVR ${shortTime(available)} available • ${shortTime(behindMs)} behind LIVE • FF/RW repeat or hold = faster"
                    }
                    else -> "DVR building… ${String.format(java.util.Locale.US, "%.1f", playerBufferMs / 1000.0)}s"
                }
                Text(status, color = Muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (queue.size > 1) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Channels", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                            }
            Spacer(Modifier.height(2.dp))
            LazyColumn(
                state = lineupState,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(queue, key = { _, ch -> ch.url }) { itemIndex, ch ->
                    val selectedNow = itemIndex == currentIdx
                    // ZAKO_V425_MINI_EPG: lookup only this composed row.
                    // ZAKO_V431_MINI_TITLE: resolve from current guide data every composition.
                    // Some providers key mini-player rows differently; fall back to epgId.
                    val rowProgram = run {
                        val t = System.currentTimeMillis()
                        EpgStore.guide(ch.guideKey, ch.name).firstOrNull { t in it.startMs until it.endMs }
                            ?: EpgStore.guide(ch.epgId, ch.name).firstOrNull { t in it.startMs until it.endMs }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .then(if (selectedNow) Modifier.focusRequester(lineupFocus) else Modifier)
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                                    runCatching { timelineFocus.requestFocus() }
                                    true
                                } else false
                            }
                            .tvFocus(RoundedCornerShape(6.dp))
                            .background(
                                if (selectedNow) ElectricCyan.copy(alpha = 0.22f) else Color(0xCC0A2642),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { touch(); onTune(ch) }
                            .padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (selectedNow) "NOW" else "${itemIndex + 1}",
                            color = if (selectedNow) Accent else Muted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(38.dp)
                        )
                        // ZAKO_V435_LINEUP_HORIZONTAL: the three visible mini-guide rows keep
                        // the channel and current program side-by-side so the program title never
                        // drops under the channel and gets vertically clipped by the 38dp row.
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ch.name,
                                color = Ink, fontSize = 12.sp,
                                fontWeight = if (selectedNow) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(if (rowProgram != null) 0.42f else 1f)
                            )
                            if (rowProgram != null) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    rowProgram.title,
                                    color = Color(0xFFFFE45C), fontSize = 11.sp, fontWeight = FontWeight.Bold, // ZAKO_V432_MINI_YELLOW
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.58f)
                                )
                            }
                        }
                        if (rowProgram != null) {
                            Text(
                                "${fmt.format(Date(rowProgram.startMs))}–${fmt.format(Date(rowProgram.endMs))}",
                                color = LimeBubble, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showRecent) {
            Spacer(Modifier.height(4.dp))
            if (entries.isEmpty()) {
                Text("No previous channels yet.", color = Muted, fontSize = 10.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Previous", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("OK tunes • Back closes", color = Ink, fontSize = 9.sp)
                }
                Spacer(Modifier.height(3.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    itemsIndexed(entries) { itemIndex, ch ->
                        val recentNow = remember(ch.url, EpgStore.loaded.value) {
                            val t = System.currentTimeMillis()
                            EpgStore.guide(ch.guideKey, ch.name).firstOrNull { t in it.startMs until it.endMs }?.title
                        }
                        Row(
                            Modifier
                                .width(170.dp)
                                .then(if (itemIndex == 0) Modifier.focusRequester(recentFocus) else Modifier)
                                .focusProperties { up = timelineFocus }
                                .tvFocus(RoundedCornerShape(9.dp))
                                .background(Color(0x55202634), RoundedCornerShape(9.dp))
                                .clickable { touch(); onTune(ch) }
                                .padding(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelIcon(ch.name, ch.artwork, 34.dp)
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(recentNow ?: "Press OK to watch", color = if (recentNow != null) ProgramCyan else Ink, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
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
    activeColor: Color = Ink,
    onClick: () -> Unit
) {
    Text(
        label,
        color = if (enabled) activeColor else Muted.copy(alpha = 0.62f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .tvFocus(RoundedCornerShape(14.dp))
            .background(Color(0x66202634), RoundedCornerShape(14.dp))
            // Keep unavailable buttons focusable so the D-pad grid never hits
            // a dead end. Pressing one gives the user the reason it is
            // unavailable (for example DIRECT RESCUE vs DVR Live).
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 6.dp)
    )
}

@Composable
private fun MiniGuideChip(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    onClick: () -> Unit
) {
    Text(
        label,
        color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
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
