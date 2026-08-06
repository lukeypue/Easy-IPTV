package com.easyiptv.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private fun Modifier.tvFocus(shape: RoundedCornerShape = RoundedCornerShape(14.dp)): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        this
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Accent else Color.Transparent,
                shape = shape
            )
    }

/* ----------------------------- navigation ----------------------------- */

data class Playable(
    val name: String,
    val url: String,
    val isLive: Boolean,
    val epgId: String? = null,
    val guideKey: String? = null,
    val canRecord: Boolean = false
)

sealed class Nav {
    object Home : Nav()
    data class Play(
        val queue: List<Playable>,
        val start: Int = 0,
        val from: Nav = Home,
        val startAtMs: Long? = null   // jump straight to this spot (mini-player resume)
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

    // The corner mini player: whatever you backed out of keeps playing here.
    var mini by remember { mutableStateOf<MiniState?>(null) }
    // Only auto-tune to the last channel once per app start.
    var autoTuned by remember { mutableStateOf(false) }

    fun openPlay(p: Nav.Play) {
        mini = null           // full screen takes over — never two streams at once
        if (p.queue.getOrNull(p.start)?.isLive == true) {
            val ch = p.queue[p.start]
            prefs.edit()
                .putString("last_live_name", ch.name)
                .putString("last_live_url", ch.url)
                .putString("last_live_epg", ch.epgId ?: "")
                .putString("last_live_guide", ch.guideKey ?: "")
                .apply()
        }
        nav = p
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
            // 1) Open INSTANTLY with the saved copy from last time (if we have one).
            if (cacheKey != null) {
                val cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    DataCache.load(context, cacheKey)
                }
                if (cached != null) data = cached
            }
            // 2) Then quietly refresh from the provider in the background.
            try {
                val fresh = source.loadAll()
                data = fresh
                if (cacheKey != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        DataCache.save(context, cacheKey, fresh)
                    }
                }
            } catch (e: Exception) {
                // Only show an error if we had nothing cached to fall back on.
                if (data == null) loadError = e.message ?: "error"
            }
        }
    }

    // Once channels are in, quietly download the full TV guide in the background.
    LaunchedEffect(data) {
        val s = source
        if (data != null && s != null) {
            EpgStore.load(s.xmltvUrl())
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
        val idx = channels.indexOfFirst { it.url == url || liveStreamUrl(prefs, it.url) == url }
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
                source = source,
                prefs = prefs,
                onBack = { idx, posMs ->
                    // Back doesn't stop the show — it shrinks into the corner
                    // so you can keep browsing while it plays.
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
            activeIdx = activeIdx,
            section = railSection,
            depth = railDepth,
            mini = mini,
            onResumeMini = {
                val m = mini ?: return@HomeScreen
                openPlay(
                    Nav.Play(
                        m.queue, m.index, from = Nav.Home,
                        startAtMs = if (m.queue.getOrNull(m.index)?.isLive == false) m.posMs else null
                    )
                )
            },
            onCloseMini = { mini = null },
            onRoot = { id ->
                railSection = id
                railDepth = if (id == "live" || id == "movies" || id == "series") 1 else 0
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
                Text("Easy IPTV", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Ink)
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
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Give it a name (optional)") },
                placeholder = { Text("e.g. Home, Sports, Backup") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (type == "m3u") {
                OutlinedTextField(
                    value = m3u, onValueChange = { m3u = it },
                    label = { Text("Playlist link") },
                    placeholder = { Text("http://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("Server address") },
                    placeholder = { Text("http://yourserver.com:8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            containerColor = SurfaceCol,
            title = { Text("Leave Easy IPTV?", color = Ink) },
            text = {
                Text(
                    "Downloads in progress and scheduled DVR recordings keep working in the background even after you exit — the device just needs to stay powered on.",
                    color = Muted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) { Text("Exit", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showExit = false }) { Text("Stay", color = Muted) }
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
                Text("Easy IPTV", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
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
                    when {
                        depth == 1 && section == "live" -> LivePane(prefs, activeIdx, data!!, liveCat, miniActive = mini != null, onPlayLive)
                        depth == 1 && section == "movies" -> MoviesPane(prefs, data!!, movieCat, onPlay)
                        depth == 1 && section == "series" -> SeriesPane(source, data!!, seriesCat, onSeries)
                        section == "search" -> SearchTab(prefs, data!!, searchQuery, onSearchQuery, onPlay, onSeries)
                        section == "downloads" -> DownloadsPane(prefs, onPlay)
                        section == "recordings" -> RecordingsPane(prefs, onPlay)
                        section == "playlists" -> PlaylistsPane(playlists, activeIdx, onSelectPlaylist, onDeletePlaylist, onAddPlaylist)
                        section == "settings" -> SettingsPane(prefs)
                        else -> Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Pick a section on the left.", color = Muted, fontSize = 14.sp)
                        }
                    }
                }
                // What you were watching keeps playing here while you browse.
                // Highlight it and press OK to go back full screen.
                if (mini != null) {
                    val mp = mini.queue.getOrNull(mini.index)
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
                                MiniVideo(
                                    url = mp.url,
                                    startMs = if (mp.isLive) 0L else mini.posMs
                                )
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
    LazyColumn(
        modifier = Modifier.width(126.dp).fillMaxHeight().background(SurfaceCol),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        if (depth == 0) {
            items(RootItems) { p ->
                RailItem(p.second, section == p.first) { onRoot(p.first) }
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
private fun RailItem(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
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
private fun ErrorBox(err: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn't load your playlist", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your service didn't answer. Check your internet, or tell Claude what it says here: $err",
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
    miniActive: Boolean,
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

    // Xfinity-style preview: the highlighted channel plays small in the upper
    // right. On remotes, highlighting follows the D-pad; on phones (no D-pad
    // focus) the preview starts on the first channel of the list. Debounced so
    // quickly scrolling past channels doesn't open a stream for each one.
    // When the global mini player is showing (you backed out of something),
    // that takes the corner instead — never two streams at once.
    val previewEnabled = remember { prefs.getBoolean("guide_preview", true) } && !miniActive
    var focusedCh by remember { mutableStateOf<LiveChannel?>(null) }
    var previewCh by remember { mutableStateOf<LiveChannel?>(null) }
    LaunchedEffect(filtered, previewEnabled) {
        if (previewEnabled && focusedCh == null && filtered.isNotEmpty()) {
            focusedCh = filtered.first()
        }
    }
    LaunchedEffect(focusedCh) {
        val ch = focusedCh ?: return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        previewCh = ch
    }

    // Come back to Live TV and the list is scrolled right where you left it.
    val listState = androidx.compose.runtime.saveable.rememberSaveable(
        selectedCat, saver = androidx.compose.foundation.lazy.LazyListState.Saver
    ) { androidx.compose.foundation.lazy.LazyListState() }

    Row(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxHeight()) {
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
                items(filtered) { ch ->
                    val schedule = if (guideReady) EpgStore.guide(ch.epgId, ch.name) else emptyList()
                    val now = System.currentTimeMillis()
                    val current = schedule.firstOrNull { now in it.startMs until it.endMs }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .tvFocus()
                            .onFocusChanged { st -> if (st.isFocused) focusedCh = ch }
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
                                                    Recorder.start(context, ch.url, "${e.title} (${ch.name})", e.endMs + 2 * 60 * 1000)
                                                    toast(context, "Recording \"${e.title}\" until it ends.")
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
    // Upper-right preview panel (Xfinity-style)
    if (previewEnabled && previewCh != null) {
        val pch = previewCh!!
        Column(
            Modifier
                .width(236.dp)
                .fillMaxHeight()
                .padding(start = 4.dp, end = 10.dp, top = 6.dp)
        ) {
            MiniVideo(url = liveStreamUrl(prefs, pch.url), startMs = 0L)
            Spacer(Modifier.height(8.dp))
            Text(
                pch.name,
                color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (guideReady) {
                val sched = EpgStore.guide(pch.epgId, pch.name)
                val nowMs = System.currentTimeMillis()
                val nowShow = sched.firstOrNull { nowMs in it.startMs until it.endMs }
                val nextShow = sched.firstOrNull { it.startMs > nowMs }
                if (nowShow != null) {
                    Text(
                        "Now: ${nowShow.title}",
                        color = Accent, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                if (nextShow != null) {
                    Text(
                        "Next at ${fmt.format(Date(nextShow.startMs))}: ${nextShow.title}",
                        color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Press OK to watch full screen.", color = Muted, fontSize = 10.sp)
        }
    }
    }
}

/* Small always-on player for the corner — used by both the guide preview and
 * the global mini player. Lean buffer so it starts fast; released the moment
 * it leaves the screen. Handles streams and downloaded/recorded local files. */
@OptIn(UnstableApi::class)
@Composable
private fun MiniVideo(url: String, startMs: Long) {
    val context = LocalContext.current
    val exo = remember {
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2_000, 15_000, 500, 1_000)
                    .build()
            )
            .build()
    }
    LaunchedEffect(url) {
        val uri = if (url.startsWith("/")) Uri.fromFile(File(url)) else Uri.parse(url)
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        if (startMs > 0) exo.seekTo(startMs)
        exo.playWhenReady = true
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }
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
                    player = exo
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { it.player = exo },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/* Live channels can be pulled from Xtream providers two ways:
 *  - Standard (.ts): one continuous stream. Required for recording.
 *  - Smooth (HLS .m3u8): chopped into small chunks like YouTube/Prime use —
 *    recovers from hiccups better, and if the provider offers several quality
 *    levels the player auto-switches to match the connection. */
private fun liveStreamUrl(prefs: SharedPreferences, url: String): String =
    if (prefs.getString("live_format", "ts") == "hls" && url.endsWith(".ts"))
        url.removeSuffix(".ts") + ".m3u8"
    else url

private fun livePlayable(prefs: SharedPreferences, ch: LiveChannel): Playable {
    val u = liveStreamUrl(prefs, ch.url)
    return Playable(
        name = ch.name,
        url = u,
        isLive = true,
        epgId = ch.id,
        guideKey = ch.epgId,
        canRecord = u.endsWith(".ts")
    )
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
                onClick = { onPlay(Playable(m.name, m.url, isLive = false)) },
                trailing = {
                    IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
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
            MediaRow(name = s.name, icon = s.icon, onClick = { onSeries(s) }, trailing = {})
        }
    }
}

/* ----------------------------- settings pane ----------------------------- */
@Composable
fun SettingsPane(prefs: SharedPreferences) {
    var bufferSec by remember { mutableIntStateOf(prefs.getInt("buffer_sec", 30)) }
    var guidePreview by remember { mutableStateOf(prefs.getBoolean("guide_preview", true)) }
    var autoLast by remember { mutableStateOf(prefs.getBoolean("autoplay_last", true)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp)
    ) {
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
        Text("Live stream format", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Smooth (HLS) delivers live TV in small chunks — the same way YouTube and Prime do — which recovers from internet hiccups better. Try it if channels stutter. Note: the record button while watching only works in Standard.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        var liveFormat by remember { mutableStateOf(prefs.getString("live_format", "ts") ?: "ts") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Standard", liveFormat == "ts") {
                liveFormat = "ts"; prefs.edit().putString("live_format", "ts").apply()
            }
            Chip("Smooth (HLS)", liveFormat == "hls") {
                liveFormat = "hls"; prefs.edit().putString("live_format", "hls").apply()
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Guide preview", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Plays the highlighted channel in the corner while you browse Live TV, like cable boxes do.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("On", guidePreview) { guidePreview = true; prefs.edit().putBoolean("guide_preview", true).apply() }
            Chip("Off", !guidePreview) { guidePreview = false; prefs.edit().putBoolean("guide_preview", false).apply() }
        }

        Spacer(Modifier.height(24.dp))
        Text("Easy IPTV 3.6 — plays the playlists you provide. This app includes no channels or content of its own.", fontSize = 11.sp, color = Muted)
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
    onSeries: (SeriesItem) -> Unit
) {
    var recents by remember { mutableStateOf(loadRecents(prefs)) }

    fun saveRecent(q: String) {
        addRecent(prefs, q)
        recents = loadRecents(prefs)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = onQuery,
            placeholder = { Text("Search live, movies & series…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).tvFocus(RoundedCornerShape(8.dp))
        )
        Text(
            "Matches any part of a name — \"wars\" finds Star Wars.",
            fontSize = 11.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

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
                    MediaRow(ch.name, ch.icon, onClick = { saveRecent(q); onPlay(livePlayable(prefs, ch)) }, trailing = {})
                }
            }
            if (movieHits.isNotEmpty()) {
                item { SectionHeader("Movies") }
                items(movieHits) { m ->
                    MediaRow(m.name, m.icon, onClick = { saveRecent(q); onPlay(Playable(m.name, m.url, isLive = false)) }, trailing = {})
                }
            }
            if (seriesHits.isNotEmpty()) {
                item { SectionHeader("Series") }
                items(seriesHits) { s ->
                    MediaRow(s.name, s.icon, onClick = { saveRecent(q); onSeries(s) }, trailing = {})
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
            err != null -> ErrorBox(err!!) { err = null }
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
                            trailing = {
                                IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
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

    LaunchedEffect(Unit) {
        while (true) {
            val inFlight = items.filter { d -> File(d.path).let { !it.exists() || it.length() == 0L } }
            if (inFlight.isNotEmpty()) {
                val m = HashMap<Long, Pair<Long, Long>>()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    inFlight.forEach { d ->
                        DownloadStore.progress(context, d.id)?.let { m[d.id] = it }
                    }
                }
                progressMap = m
                items = DownloadStore.load(prefs)   // pick up ones that just finished
            }
            kotlinx.coroutines.delay(1_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Saved for offline watching. Each download is kept for 14 days, then removed automatically. Downloads keep going even if you close the app.",
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
                    val ready = f.exists() && f.length() > 0
                    val daysLeft = ((d.expires - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                    val prog = progressMap[d.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                    "$daysLeft day${if (daysLeft == 1L) "" else "s"} left",
                                    color = Muted, fontSize = 12.sp
                                )
                            } else {
                                val done = prog?.first ?: 0L
                                val total = prog?.second ?: -1L
                                val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else null
                                Text(
                                    when {
                                        pct != null -> "Downloading… $pct%  (${done / 1_048_576} MB of ${total / 1_048_576} MB)"
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
                        IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                            DownloadStore.stopAndRemove(context, prefs, d)
                            items = DownloadStore.load(prefs)
                        }) {
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

    Column(Modifier.fillMaxSize()) {
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
                    val mb = f.length() / (1024 * 1024)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            Text("$mb MB", color = Muted, fontSize = 12.sp)
                        }
                        IconButton(modifier = Modifier.tvFocus(RoundedCornerShape(24.dp)), onClick = {
                            f.delete()
                            files = Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                        }) {
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

@Composable
private fun ChannelIcon(name: String, icon: String?) {
    Box(
        modifier = Modifier.size(46.dp).background(Surface2, RoundedCornerShape(10.dp)),
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
private fun MediaRow(
    name: String,
    icon: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        trailing()
    }
}

/* ----------------------------- player ----------------------------- */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    queue: List<Playable>,
    start: Int,
    startAtMs: Long? = null,
    source: Source?,
    prefs: SharedPreferences,
    onBack: (index: Int, posMs: Long) -> Unit
) {
    // Safety: never crash on an empty queue — just go back.
    if (queue.isEmpty()) {
        LaunchedEffect(Unit) { onBack(0, 0L) }
        return
    }
    val context = LocalContext.current
    val bufferSec = remember { prefs.getInt("buffer_sec", 30) }
    var resizeMode by remember {
        mutableIntStateOf(prefs.getInt("resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT))
    }
    var currentIdx by remember { mutableIntStateOf(start.coerceIn(0, queue.size - 1)) }
    val current = queue[currentIdx.coerceIn(0, queue.size - 1)]
    var nowNext by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    // Our top bar shows and hides in lockstep with the player's own controls.
    var overlayVisible by remember { mutableStateOf(true) }
    var showRecordChoice by remember { mutableStateOf(false) }
    // Resume support: if there's a saved spot for the starting item, hold playback
    // and ask Resume / Start over. (Skipped when arriving from the mini player —
    // that already knows exactly where you were.)
    val startItem = queue[start.coerceIn(0, queue.size - 1)]
    val resumeAt = remember {
        if (startItem.isLive || startAtMs != null) null
        else WatchStore.get(prefs, startItem.url)?.let { w ->
            if (w.pos > 30_000 && (w.dur <= 0 || w.pos < (w.dur * 0.95).toLong())) w.pos else null
        }
    }
    var pendingResume by remember { mutableStateOf(resumeAt) }
    // Set when a channel has failed every reconnect attempt — it's down at the provider.
    val streamDead = remember { mutableStateOf(false) }
    var pvRef by remember { mutableStateOf<PlayerView?>(null) }
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

    val exo = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            // PREFER the bundled FFmpeg software audio decoders (AC-3, E-AC-3, DTS,
            // TrueHD, MP2, etc). Many TVs and sticks *claim* they can decode Dolby
            // audio but play silence — software decoding always produces sound.
            // Video still uses the device's hardware decoder (needed for 4K).
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            // If a decoder fails mid-stream, quietly try the next one instead of erroring.
            .setEnableDecoderFallback(true)
        // Amazon/YouTube-style buffering: start playing after just ~1.5s of video,
        // then keep filling a DEEP buffer (up to 2 minutes) behind the scenes.
        // A shaky connection eats into that stored-up video instead of stuttering.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferSec * 1000,                                    // always try to keep at least this much
                (bufferSec * 1000 * 4).coerceAtLeast(120_000),       // ...and stockpile up to 2+ minutes
                1_500,                                               // start playback after ~1.5s (fast start)
                4_000                                                // after a stall, resume once 4s is stored
            )
            // Hit the time targets even if it means using more memory.
            .setPrioritizeTimeOverSizeThresholds(true)
            // Keep the last 30s behind us so instant-rewind works without re-downloading.
            .setBackBuffer(30_000, true)
            .build()
        val mediaItems = queue.map { pl ->
            val uri = if (pl.url.startsWith("/")) Uri.fromFile(File(pl.url)) else Uri.parse(pl.url)
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(pl.name).build())
                .build()
        }
        // Many IPTV servers stream movies without a seek index. Constant-bitrate
        // seeking lets the player estimate positions so fast-forward/rewind work anyway.
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        val mediaSources = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractors)
            // Crappy internet: retry each failed chunk up to 8 times before giving up,
            // instead of erroring out on the first hiccup.
            .setLoadErrorHandlingPolicy(
                androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(8)
            )
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSources)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItems(
                    mediaItems,
                    start.coerceIn(0, queue.size - 1),
                    startAtMs ?: C.TIME_UNSET
                )
                addListener(object : Player.Listener {
                    // If the connection fully drops, quietly rejoin the stream instead of
                    // showing an error — up to 6 tries, waiting a bit longer each time.
                    private var retries = 0

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            retries = 0
                            streamDead.value = false
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        if (retries >= 6) {
                            // Everything failed — this one is down at the provider's end.
                            streamDead.value = true
                            return
                        }
                        retries++
                        val wait = (1_000L * retries).coerceAtMost(5_000L)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            runCatching {
                                val idx = currentMediaItemIndex
                                // If Smooth (HLS) keeps failing on this channel, the provider
                                // probably doesn't support it — quietly switch this session
                                // back to the standard stream and keep going.
                                if (retries >= 3 && queue.size == 1 &&
                                    idx in queue.indices && queue[idx].isLive
                                ) {
                                    val u = currentMediaItem?.localConfiguration?.uri?.toString()
                                    if (u != null && u.endsWith(".m3u8")) {
                                        setMediaItem(
                                            MediaItem.Builder()
                                                .setUri(u.removeSuffix(".m3u8") + ".ts")
                                                .setMediaMetadata(
                                                    MediaMetadata.Builder()
                                                        .setTitle(queue[idx].name).build()
                                                )
                                                .build()
                                        )
                                    }
                                }
                                // Live TV: jump back to the "live edge" when rejoining.
                                if (idx in queue.indices && queue[idx].isLive) seekToDefaultPosition()
                                prepare()
                                play()
                            }
                        }, wait)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val prev = currentIdx
                        currentIdx = currentMediaItemIndex
                        // Rolling into the next episode = the last one is watched.
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                            prev in queue.indices && !queue[prev].isLive
                        ) {
                            WatchStore.markWatched(prefs, queue[prev].url)
                        }
                        // Channel-surfed to a new channel: remember it as "last watched"
                        // so next app start tunes here.
                        val cur = queue.getOrNull(currentMediaItemIndex)
                        if (cur?.isLive == true) {
                            prefs.edit()
                                .putString("last_live_name", cur.name)
                                .putString("last_live_url", cur.url)
                                .putString("last_live_epg", cur.epgId ?: "")
                                .putString("last_live_guide", cur.guideKey ?: "")
                                .apply()
                        }
                    }
                })
                prepare()
                playWhenReady = resumeAt == null
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                val i = exo.currentMediaItemIndex
                if (i in queue.indices && !queue[i].isLive && exo.currentPosition > 10_000) {
                    WatchStore.setProgress(
                        prefs, queue[i].url, exo.currentPosition,
                        if (exo.duration > 0) exo.duration else 0L
                    )
                }
            }
            exo.release()
        }
    }
    // If the person presses Home / switches apps, pause — no ghost audio in the background.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) exo.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    BackHandler {
        if (overlayVisible) {
            pvRef?.hideController()
            overlayVisible = false
        } else {
            onBack(exo.currentMediaItemIndex, exo.currentPosition.coerceAtLeast(0L))
        }
    }

    // Channel surfing: +1 / −1 through the category, wrapping at the ends —
    // exactly like the channel up/down buttons on a cable remote.
    fun zapReady(): Boolean =
        queue.size > 1 && queue.getOrNull(exo.currentMediaItemIndex)?.isLive == true
    fun zap(dir: Int) {
        streamDead.value = false
        val next = (exo.currentMediaItemIndex + dir + queue.size) % queue.size
        exo.seekTo(next, C.TIME_UNSET)
        exo.prepare()          // also recovers if the last channel had errored out
        exo.playWhenReady = true
        // Flash the channel name so they see where they landed.
        pvRef?.showController()
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
                android.view.KeyEvent.KEYCODE_CHANNEL_UP ->
                    if (zapReady()) { zap(-1); true } else false
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN ->
                    if (zapReady()) { zap(+1); true } else false
                // D-pad up/down: with the menus hidden they zap channels;
                // with the menus showing they navigate the menus as usual.
                android.view.KeyEvent.KEYCODE_DPAD_UP ->
                    if (zapReady() && !overlayVisible) { zap(-1); true } else false
                android.view.KeyEvent.KEYCODE_DPAD_DOWN ->
                    if (zapReady() && !overlayVisible) { zap(+1); true } else false
                else -> false
            }
        }
        PlayerKeys.handler = { key ->
            when (key) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    exo.playWhenReady = !exo.playWhenReady; true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { exo.playWhenReady = true; true }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { exo.playWhenReady = false; true }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { exo.seekForward(); true }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> { exo.seekBack(); true }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    pvRef?.showController(); true
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

    val recordingThis = Recorder.activeName.value == current.name

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Phones & tablets: swipe up = previous channel in the list,
            // swipe down = next. Taps still work normally for the controls.
            .pointerInput(queue.size) {
                var totalDrag = 0f
                androidx.compose.foundation.gestures.detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (zapReady()) {
                            when {
                                totalDrag < -120f -> zap(+1)   // swiped up
                                totalDrag > 120f -> zap(-1)    // swiped down
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
                    useController = true
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
                    // Show/hide our top bar together with the player's controls, and
                    // when they hide, pull remote focus back onto the video view so
                    // the next press is never lost.
                    val pv = this
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { vis ->
                            overlayVisible = vis == android.view.View.VISIBLE
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
            modifier = Modifier.fillMaxSize()
        )
        // Channel gave up after every reconnect attempt — tell them plainly.
        if (streamDead.value) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC15181E), RoundedCornerShape(14.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("This channel isn't answering", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "It's most likely down at your provider right now — not your internet or this device. Try another channel, or try this one again in a bit.",
                    color = Muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.tvFocus(RoundedCornerShape(22.dp)),
                    onClick = {
                        streamDead.value = false
                        runCatching {
                            if (current.isLive) exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.play()
                        }
                    }
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
                    onBack(exo.currentMediaItemIndex, exo.currentPosition.coerceAtLeast(0L))
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
                        val next = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        resizeMode = next
                        prefs.edit().putInt("resize_mode", next).apply()
                        val label = when (next) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch — fills the whole screen"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom — crops the edges"
                            else -> "Fit — whole picture, may have black bars"
                        }
                        toast(context, label)
                    }
                ) {
                    Icon(
                        Icons.Filled.AspectRatio,
                        contentDescription = "Screen shape: fit, stretch, or zoom",
                        tint = Color.White
                    )
                }
                if (current.canRecord) {
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
                        if (nowShow != null) {
                            Recorder.start(context, current.url, "${nowShow.title} (${current.name})", nowShow.endMs + 2 * 60 * 1000)
                            toast(context, "Recording until this show ends.")
                        } else {
                            Recorder.start(context, current.url, current.name)
                            toast(context, "Recording started. Tap the red button again to stop.")
                        }
                    }) {
                        Text(if (nowShow != null) "Record this show" else "Start recording", color = Accent)
                    }
                },
                dismissButton = {
                    if (nowShow != null) {
                        TextButton(onClick = {
                            showRecordChoice = false
                            Recorder.start(context, current.url, current.name)
                            toast(context, "Recording until you stop it.")
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
