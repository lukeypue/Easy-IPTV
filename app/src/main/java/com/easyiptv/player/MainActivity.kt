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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
    val canRecord: Boolean = false
)

sealed class Nav {
    object Home : Nav()
    data class Play(val queue: List<Playable>, val start: Int = 0) : Nav()
    data class Series(val s: SeriesItem) : Nav()
    object SearchAll : Nav()
    object Downloads : Nav()
    object Recordings : Nav()
    object Playlists : Nav()
    object AddPlaylist : Nav()
}

/* ----------------------------- activity ----------------------------- */
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

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DownloadStore.cleanup(prefs)
        }
    }

    if (activeIdx >= playlists.size) activeIdx = 0
    val source = remember(playlists, activeIdx) {
        playlists.getOrNull(activeIdx)?.let { buildSource(it) }
    }

    LaunchedEffect(source, reload) {
        data = null
        loadError = null
        if (source != null) {
            try {
                data = source.loadAll()
            } catch (e: Exception) {
                loadError = e.message ?: "error"
            }
        }
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
        nav is Nav.AddPlaylist -> AddPlaylistScreen(first = false, onSaved = { addPlaylist(it) }, onBack = { nav = Nav.Playlists })
        nav is Nav.Play -> {
            val pl = nav as Nav.Play
            PlayerScreen(
                queue = pl.queue,
                start = pl.start,
                source = source,
                prefs = prefs,
                onBack = { nav = Nav.Home }
            )
        }
        nav is Nav.Series && source != null -> SeriesDetailScreen(
            source = source,
            s = (nav as Nav.Series).s,
            prefs = prefs,
            onPlayQueue = { q, i -> nav = Nav.Play(q, i) },
            onBack = { nav = Nav.Home }
        )
        nav is Nav.SearchAll -> SearchScreen(
            data = data,
            onPlay = { nav = Nav.Play(listOf(it)) },
            onSeries = { nav = Nav.Series(it) },
            onBack = { nav = Nav.Home }
        )
        nav is Nav.Downloads -> DownloadsScreen(prefs, onPlay = { nav = Nav.Play(listOf(it)) }, onBack = { nav = Nav.Home })
        nav is Nav.Recordings -> RecordingsScreen(onPlay = { nav = Nav.Play(listOf(it)) }, onBack = { nav = Nav.Home })
        nav is Nav.Playlists -> PlaylistsScreen(
            playlists = playlists,
            activeIdx = activeIdx,
            onSelect = { i ->
                activeIdx = i
                PlaylistStore.setActive(prefs, i)
                reload++
                nav = Nav.Home
            },
            onDelete = { i ->
                val next = playlists.toMutableList().apply { removeAt(i) }
                playlists = next
                PlaylistStore.save(prefs, next)
                if (activeIdx >= next.size) {
                    activeIdx = 0
                    PlaylistStore.setActive(prefs, 0)
                }
                reload++
            },
            onAdd = { nav = Nav.AddPlaylist },
            onBack = { nav = Nav.Home }
        )
        else -> HomeScreen(
            prefs = prefs,
            playlistName = playlists.getOrNull(activeIdx)?.name ?: "",
            source = source,
            data = data,
            loadError = loadError,
            activeIdx = activeIdx,
            onRetry = { reload++ },
            onPlay = { nav = Nav.Play(listOf(it)) },
            onSeries = { nav = Nav.Series(it) },
            onSearch = { nav = Nav.SearchAll },
            onDownloads = { nav = Nav.Downloads },
            onRecordings = { nav = Nav.Recordings },
            onPlaylists = { nav = Nav.Playlists }
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

/* ----------------------------- home + tabs ----------------------------- */
@Composable
fun HomeScreen(
    prefs: SharedPreferences,
    playlistName: String,
    source: Source?,
    data: AppData?,
    loadError: String?,
    activeIdx: Int,
    onRetry: () -> Unit,
    onPlay: (Playable) -> Unit,
    onSeries: (SeriesItem) -> Unit,
    onSearch: () -> Unit,
    onDownloads: () -> Unit,
    onRecordings: () -> Unit,
    onPlaylists: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Easy IPTV", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Accent, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(playlistName, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (Recorder.activeName.value != null) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = "Recording", tint = Live)
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onSearch, modifier = Modifier.tvFocus(RoundedCornerShape(24.dp))) {
                Icon(Icons.Filled.Search, contentDescription = "Search everything", tint = Muted)
            }
        }

        // tab body
        Box(Modifier.weight(1f)) {
            when {
                data == null && loadError == null -> LoadingBox("Loading your playlist…")
                loadError != null -> ErrorBox(loadError, onRetry)
                else -> when (tab) {
                    0 -> LiveTab(prefs, activeIdx, data!!, onPlay)
                    1 -> MoviesTab(prefs, data!!, onPlay)
                    2 -> SeriesTab(source, data!!, onSeries)
                    3 -> GuideTab(source, data!!, onPlay)
                    else -> MoreTab(prefs, onDownloads, onRecordings, onPlaylists)
                }
            }
        }

        NavigationBar(containerColor = SurfaceCol) {
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF20160A),
                selectedTextColor = Accent,
                indicatorColor = Accent,
                unselectedIconColor = Muted,
                unselectedTextColor = Muted
            )
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, colors = itemColors,
                icon = { Icon(Icons.Filled.LiveTv, contentDescription = null) }, label = { Text("Live") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, colors = itemColors,
                icon = { Icon(Icons.Filled.Movie, contentDescription = null) }, label = { Text("Movies") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, colors = itemColors,
                icon = { Icon(Icons.Filled.Tv, contentDescription = null) }, label = { Text("Series") })
            NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, colors = itemColors,
                icon = { Icon(Icons.Filled.Today, contentDescription = null) }, label = { Text("Guide") })
            NavigationBarItem(selected = tab == 4, onClick = { tab = 4 }, colors = itemColors,
                icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) }, label = { Text("More") })
        }
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

/* ----------------------------- live tab ----------------------------- */
@Composable
fun LiveTab(prefs: SharedPreferences, activeIdx: Int, data: AppData, onPlay: (Playable) -> Unit) {
    val favKey = "fav_live_$activeIdx"
    var selectedCat by remember(activeIdx) { mutableStateOf("all") }
    var favs by remember(activeIdx) { mutableStateOf(prefs.getStringSet(favKey, emptySet())?.toSet() ?: emptySet()) }

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

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Chip("★ Favorites", selectedCat == "fav") { selectedCat = "fav" } }
            item { Chip("All", selectedCat == "all") { selectedCat = "all" } }
            items(data.liveCats) { cat ->
                Chip(cat.name, selectedCat == cat.id) { selectedCat = cat.id }
            }
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { ch ->
                    MediaRow(
                        name = ch.name,
                        icon = ch.icon,
                        onClick = { onPlay(livePlayable(ch)) },
                        trailing = {
                            IconButton(onClick = { toggleFav(ch.id) }) {
                                Icon(
                                    if (favs.contains(ch.id)) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (favs.contains(ch.id)) Accent else Muted
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun livePlayable(ch: LiveChannel) = Playable(
    name = ch.name,
    url = ch.url,
    isLive = true,
    epgId = ch.id,
    canRecord = ch.url.endsWith(".ts")
)

/* ----------------------------- movies tab ----------------------------- */
@Composable
fun MoviesTab(prefs: SharedPreferences, data: AppData, onPlay: (Playable) -> Unit) {
    val context = LocalContext.current
    var selectedCat by remember { mutableStateOf("all") }

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

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Chip("All", selectedCat == "all") { selectedCat = "all" } }
            items(data.vodCats) { cat ->
                Chip(cat.name, selectedCat == cat.id) { selectedCat = cat.id }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { m ->
                MediaRow(
                    name = m.name,
                    icon = m.icon,
                    onClick = { onPlay(Playable(m.name, m.url, isLive = false)) },
                    trailing = {
                        IconButton(onClick = {
                            toast(context, DownloadStore.start(context, prefs, m.name, m.url))
                        }) {
                            Icon(Icons.Filled.Download, contentDescription = "Download for offline", tint = Muted)
                        }
                    }
                )
            }
        }
    }
}

/* ----------------------------- series tab ----------------------------- */
@Composable
fun SeriesTab(source: Source?, data: AppData, onSeries: (SeriesItem) -> Unit) {
    var selectedCat by remember { mutableStateOf("all") }

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

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Chip("All", selectedCat == "all") { selectedCat = "all" } }
            items(data.seriesCats) { cat ->
                Chip(cat.name, selectedCat == cat.id) { selectedCat = cat.id }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { s ->
                MediaRow(name = s.name, icon = s.icon, onClick = { onSeries(s) }, trailing = {})
            }
        }
    }
}

/* ----------------------------- guide tab ----------------------------- */
@Composable
fun GuideTab(source: Source?, data: AppData, onPlay: (Playable) -> Unit) {
    var selectedCat by remember { mutableStateOf("all") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var schedule by remember { mutableStateOf<List<EpgEntry>?>(null) }
    var loadingEpg by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    if (source == null || !source.supportsEpg) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TV Guide needs an Xtream login", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "The guide works with a username & password (Xtream) playlist. M3U link playlists don't carry program info.",
                fontSize = 13.sp, color = Muted
            )
        }
        return
    }

    fun open(ch: LiveChannel) {
        if (expandedId == ch.id) { expandedId = null; return }
        expandedId = ch.id
        schedule = null
        loadingEpg = true
        scope.launch {
            val e = source.epg(ch.id, 12)
            if (expandedId == ch.id) {
                schedule = e
                loadingEpg = false
            }
        }
    }

    val filtered = data.live.filter { selectedCat == "all" || it.categoryId == selectedCat }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Tap any channel to see what's on now and later.",
            fontSize = 12.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Chip("All", selectedCat == "all") { selectedCat = "all" } }
            items(data.liveCats) { cat ->
                Chip(cat.name, selectedCat == cat.id) { selectedCat = cat.id }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { ch ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .tvFocus()
                        .background(SurfaceCol, RoundedCornerShape(14.dp))
                        .clickable { open(ch) }
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelIcon(ch.name, ch.icon)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            ch.name,
                            modifier = Modifier.weight(1f),
                            color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { onPlay(livePlayable(ch)) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Watch", tint = Accent)
                        }
                    }
                    if (expandedId == ch.id) {
                        Spacer(Modifier.height(6.dp))
                        when {
                            loadingEpg -> Text("Loading guide…", fontSize = 13.sp, color = Muted)
                            schedule.isNullOrEmpty() -> Text(
                                "No guide info for this channel.",
                                fontSize = 13.sp, color = Muted
                            )
                            else -> {
                                val now = System.currentTimeMillis()
                                schedule!!.forEach { e ->
                                    val isNow = now in e.startMs until e.endMs
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            fmt.format(Date(e.startMs)),
                                            fontSize = 12.sp,
                                            color = if (isNow) Accent else Muted,
                                            modifier = Modifier.width(72.dp)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                (if (isNow) "NOW  •  " else "") + e.title,
                                                fontSize = 13.sp,
                                                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isNow) Ink else Muted,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis
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

/* ----------------------------- more tab ----------------------------- */
@Composable
fun MoreTab(
    prefs: SharedPreferences,
    onDownloads: () -> Unit,
    onRecordings: () -> Unit,
    onPlaylists: () -> Unit
) {
    var bufferSec by remember { mutableIntStateOf(prefs.getInt("buffer_sec", 20)) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        MoreRow("Downloads", "Movies & episodes saved for offline (kept 7 days)", onDownloads)
        Spacer(Modifier.height(10.dp))
        MoreRow("Recordings", "Live TV you've recorded", onRecordings)
        Spacer(Modifier.height(10.dp))
        MoreRow("Playlists", "Add, switch, or remove playlists (up to 3)", onPlaylists)

        Spacer(Modifier.height(24.dp))
        Text("Stream buffer", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "A bigger buffer smooths out choppy streams. Recommended: 20 seconds.",
            fontSize = 12.sp, color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Small (5s)", bufferSec == 5) { bufferSec = 5; prefs.edit().putInt("buffer_sec", 5).apply() }
            Chip("Normal (20s)", bufferSec == 20) { bufferSec = 20; prefs.edit().putInt("buffer_sec", 20).apply() }
            Chip("Big (40s)", bufferSec == 40) { bufferSec = 40; prefs.edit().putInt("buffer_sec", 40).apply() }
        }

        Spacer(Modifier.height(24.dp))
        Text("Easy IPTV 2.0 — plays the playlists you provide. This app includes no channels or content of its own.", fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocus()
            .background(SurfaceCol, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}

/* ----------------------------- search everything ----------------------------- */
@Composable
fun SearchScreen(
    data: AppData?,
    onPlay: (Playable) -> Unit,
    onSeries: (SeriesItem) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Muted)
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search live, movies & series…") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "Matches any part of a name — \"wars\" finds Star Wars.",
            fontSize = 11.sp, color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (data == null) {
            LoadingBox("Loading your playlist…")
            return
        }

        val q = query.trim()
        if (q.length < 2) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Type at least 2 letters to search.", color = Muted, fontSize = 14.sp)
            }
            return
        }

        val liveHits = data.live.filter { it.name.contains(q, ignoreCase = true) }.take(30)
        val movieHits = data.movies.filter { it.name.contains(q, ignoreCase = true) }.take(30)
        val seriesHits = data.series.filter { it.name.contains(q, ignoreCase = true) }.take(30)

        if (liveHits.isEmpty() && movieHits.isEmpty() && seriesHits.isEmpty()) {
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
                    MediaRow(ch.name, ch.icon, onClick = { onPlay(livePlayable(ch)) }, trailing = {})
                }
            }
            if (movieHits.isNotEmpty()) {
                item { SectionHeader("Movies") }
                items(movieHits) { m ->
                    MediaRow(m.name, m.icon, onClick = { onPlay(Playable(m.name, m.url, isLive = false)) }, trailing = {})
                }
            }
            if (seriesHits.isNotEmpty()) {
                item { SectionHeader("Series") }
                items(seriesHits) { s ->
                    MediaRow(s.name, s.icon, onClick = { onSeries(s) }, trailing = {})
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
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Muted)
            }
            Text(
                s.name,
                fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
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
                eps!!.forEach { (season, list) ->
                    item { SectionHeader("Season $season") }
                    items(list) { ep ->
                        val label = "E${ep.episodeNum}  ${ep.title}"
                        val epName = "${s.name} S${season}E${ep.episodeNum}"
                        MediaRow(
                            name = label,
                            icon = null,
                            onClick = {
                                val idx = queue.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                                onPlayQueue(queue, idx)
                            },
                            trailing = {
                                IconButton(onClick = {
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
fun DownloadsScreen(prefs: SharedPreferences, onPlay: (Playable) -> Unit, onBack: () -> Unit) {
    var items by remember { mutableStateOf(DownloadStore.load(prefs)) }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Downloads", onBack)
        Text(
            "Saved for offline watching. Each download is kept for 7 days, then removed automatically.",
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
                            Text(
                                if (ready) "$daysLeft day${if (daysLeft == 1L) "" else "s"} left" else "Downloading…",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = {
                            DownloadStore.remove(prefs, d)
                            items = DownloadStore.load(prefs)
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

/* ----------------------------- recordings ----------------------------- */
@Composable
fun RecordingsScreen(onPlay: (Playable) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember {
        mutableStateOf(
            Recorder.recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        )
    }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Recordings", onBack)
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
                    Recorder.stop()
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
                        IconButton(onClick = {
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
fun PlaylistsScreen(
    playlists: List<Playlist>,
    activeIdx: Int,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Playlists", onBack)
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
                    IconButton(onClick = { onDelete(i) }) {
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

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Muted)
        }
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink)
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
    source: Source?,
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    // Safety: never crash on an empty queue — just go back.
    if (queue.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val bufferSec = remember { prefs.getInt("buffer_sec", 20) }
    var resizeMode by remember {
        mutableIntStateOf(prefs.getInt("resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT))
    }
    var currentIdx by remember { mutableIntStateOf(start.coerceIn(0, queue.size - 1)) }
    val current = queue[currentIdx.coerceIn(0, queue.size - 1)]
    var nowNext by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
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
        // Big buffer: hold up to bufferSec of video so shaky connections don't stutter.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferSec * 1000,
                (bufferSec * 1000 * 3).coerceAtLeast(60_000),
                2_500,
                5_000
            )
            .build()
        val mediaItems = queue.map { pl ->
            val uri = if (pl.url.startsWith("/")) Uri.fromFile(File(pl.url)) else Uri.parse(pl.url)
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(pl.name).build())
                .build()
        }
        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItems(mediaItems, start.coerceIn(0, queue.size - 1), C.TIME_UNSET)
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentIdx = currentMediaItemIndex
                    }
                })
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(Unit) {
        onDispose { exo.release() }
    }
    BackHandler { onBack() }

    LaunchedEffect(current.epgId) {
        val id = current.epgId
        if (id != null && source != null && source.supportsEpg) {
            nowNext = source.epg(id, 2)
        }
    }

    val recordingThis = Recorder.activeName.value == current.name

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = true
                    setShowNextButton(queue.size > 1)
                    setShowPreviousButton(queue.size > 1)
                }
            },
            update = { view -> view.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
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
                                Recorder.stop()
                                toast(context, "Recording saved — find it in More → Recordings.")
                            } else {
                                Recorder.start(context, current.url, current.name)
                                toast(context, "Recording started. Tap the red button again to stop.")
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
    }
}
