package com.easyiptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DecoderCounters
import kotlinx.coroutines.launch
import java.util.Locale

private val healthInk = Color(0xFFDDF8FF)
private val healthMuted = Color(0xFFADD8E6)
private val healthSurface = Color(0xFF101C2F)

private data class PanelObservation(
    val channel: String,
    val assessment: StreamHealthAssessment,
    val incoming: String,
    val demand: String,
    val buffer: String,
    val picture: String,
    val drops: String,
    val stalls: String,
    val status: String,
    val dualStatus: String,
    val qualityNote: String,
    val canLimit: Boolean,
    val limited: Boolean,
)

/** No polling exists outside the dialog; network telemetry uses the current feed. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StreamHealthDialog(onClose: () -> Unit) {
    var observation by remember { mutableStateOf<PanelObservation?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val closeFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    val autoFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val lineStep = with(LocalDensity.current) { 56.dp.toPx() }
    val maxTextHeight = LocalConfiguration.current.screenHeightDp.dp * .55f
    LaunchedEffect(refresh) {
        var previousCounters: DecoderCounters? = null
        var previousDropped = 0
        var previousRendered = 0
        while (true) {
            val player = Playback.player
            if (player == null) {
                observation = null
            } else {
                val counters = player.videoDecoderCounters
                counters?.ensureUpdated()
                val sampled = counters != null && counters === previousCounters
                val dropped = if (sampled) (counters!!.droppedBufferCount - previousDropped).coerceAtLeast(0) else 0
                val rendered = if (sampled) (counters!!.renderedOutputBufferCount - previousRendered).coerceAtLeast(0) else 0
                previousCounters = counters
                previousDropped = counters?.droppedBufferCount ?: 0
                previousRendered = counters?.renderedOutputBufferCount ?: 0
                val transfer = Playback.streamHealth.snapshot()
                val video = player.videoFormat
                val audio = player.audioFormat
                val tracks = player.currentTracks
                val selectedVideo = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                val audioPresent = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                val videoRate = video?.bitrate?.takeIf { it > 0 }?.toLong()
                val audioRate = if (audioPresent) audio?.bitrate?.takeIf { it > 0 }?.toLong() else 0L
                val demand = if (videoRate != null && audioRate != null) videoRate + audioRate else null
                val uri = player.currentMediaItem?.localConfiguration?.uri
                val local = uri?.scheme == "file" || (Timeshift.active && !Playback.simpleRaw && !Playback.directLive)
                val requested = player.playWhenReady && player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
                val error = player.playerError
                val assessment = assessStreamHealth(StreamHealthInput(
                    playRequested = requested, buffering = player.playbackState == Player.STATE_BUFFERING,
                    loading = player.isLoading, bufferedMs = player.totalBufferedDuration.coerceAtLeast(0),
                    received = transfer, formatBitrateBps = demand,
                    droppedFrames = dropped, renderedFrames = rendered,
                    decoderError = error?.errorCode in 4001..4006, localPlayback = local),
                    deliveryProgressKnown = !Playback.dualDeliveryObservationPending())
                val lower = Playback.streamQuality.canLimit(tracks)
                val limited = Playback.streamQuality.isLimited
                val adaptive = selectedVideo.any { group ->
                    group.isAdaptiveSupported && (0 until group.length).count { group.isTrackSupported(it) } > 1
                }
                fun rate(value: Long) = String.format(Locale.US, "%.2f Mbps", value / 1_000_000.0)
                val measured = transfer.receivedBitsPerSecond?.takeIf { transfer.bytesReceived > 0 || transfer.activeTransfers > 0 }
                val stats = Playback.streamStats?.playbackStats
                observation = PanelObservation(
                    channel = Playback.queue.getOrNull(Playback.currentIdxC.intValue)?.name ?: "Current playback",
                    assessment = assessment,
                    incoming = if (local) "Unavailable during local/pause playback" else measured?.let {
                        rate(it) + if (Playback.providerConnectionSlots() == 2) " useful segment payload" else " received recently"
                    } ?: "Measuring…",
                    demand = demand?.let { rate(it) + " advertised" } ?: "Not advertised by this channel",
                    buffer = String.format(Locale.US, "%.1f seconds ahead", player.totalBufferedDuration.coerceAtLeast(0) / 1000.0),
                    picture = if (video != null) {
                        val size = if (video.width > 0 && video.height > 0) "${video.width} × ${video.height}" else "Resolution unknown"
                        val fps = if (video.frameRate.isFinite() && video.frameRate > 0) String.format(Locale.US, " • %.1f fps", video.frameRate) else ""
                        "$size$fps • ${video.sampleMimeType ?: "Codec unknown"}"
                    } else "Video format not known yet",
                    drops = if (sampled) "$dropped dropped / ${dropped + rendered} frames in the last sample" else "Measuring…",
                    stalls = stats?.let { String.format(Locale.US, "%d interruptions • %.1f seconds stalled", it.totalRebufferCount, it.totalRebufferTimeMs / 1000.0) } ?: "Measuring…",
                    status = when {
                        error != null -> "Playback error ${error.errorCode}"
                        !player.playWhenReady -> "Paused"
                        player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "Playback temporarily suspended"
                        player.playbackState == Player.STATE_BUFFERING -> "Collecting video"
                        player.playbackState == Player.STATE_READY -> "Playing"
                        player.playbackState == Player.STATE_ENDED -> "Ended"
                        else -> "Waiting for playback"
                    },
                    dualStatus = Playback.dualStreamStatus,
                    qualityNote = when {
                        selectedVideo.isEmpty() -> "Quality options are not known yet."
                        limited -> "720p limit requested. The picture above shows the quality currently playing."
                        adaptive -> "Auto adapts among the quality versions supplied by this channel."
                        lower -> "A lower supported quality is available."
                        else -> "This feed has no supported 720p alternative. Making the picture smaller will not reduce incoming data."
                    }, canLimit = lower, limited = limited)
            }
            kotlinx.coroutines.delay(1_000)
        }
    }
    fun buttonUp(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
            detailsFocus.requestFocus()
            return true
        }
        return false
    }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = healthSurface,
        title = { Text("Stream health", color = healthInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = maxTextHeight)
                .verticalScroll(scroll)
                .semantics { contentDescription = "Stream health details" }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionUp -> { scope.launch { scroll.scrollTo((scroll.value - lineStep.toInt()).coerceAtLeast(0)) }; true }
                        Key.DirectionDown -> {
                            if (scroll.canScrollForward) scope.launch { scroll.scrollTo((scroll.value + lineStep.toInt()).coerceAtMost(scroll.maxValue)) }
                            else closeFocus.requestFocus()
                            true
                        }
                        Key.DirectionLeft -> { closeFocus.requestFocus(); true }
                        Key.DirectionRight -> { autoFocus.requestFocus(); true }
                        else -> false
                    }
                }.focusProperties { canFocus = true }.focusRequester(detailsFocus).focusable()) {
                val item = observation
                if (item == null) Text("No channel is currently playing.", color = healthInk)
                else {
                    Text(item.channel, color = healthInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(item.assessment.title, color = healthInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(item.assessment.detail, color = healthMuted, fontSize = 12.sp)
                    HealthField("Playback", item.status)
                    HealthField("Incoming stream", item.incoming)
                    HealthField("Stream demand", item.demand)
                    Text("Advertised bitrate can differ from actual peaks and transport overhead. Received Mbps is not a connection speed test.", color = healthMuted, fontSize = 11.sp)
                    HealthField("Playable reserve", item.buffer)
                    HealthField("Current video", item.picture)
                    HealthField("Recent frame drops", item.drops)
                    HealthField("Rebuffering this playback", item.stalls)
                    HealthField("Dual Stream Trial", item.dualStatus)
                    Text(item.qualityNote, color = healthInk, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    Text("These measurements cannot prove provider server load or rule out an upstream feed problem. No extra stream connection is opened.", color = healthMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    Playback.player?.let { Playback.streamQuality.setLimited(it, false) }; refresh++
                }, modifier = Modifier.focusProperties { canFocus = true }.focusRequester(autoFocus).onPreviewKeyEvent(::buttonUp)) {
                    Text(if (observation?.limited == true) "AUTO" else "AUTO ✓", color = healthInk)
                }
                TextButton(onClick = {
                    Playback.player?.let { Playback.streamQuality.setLimited(it, true) }; refresh++
                }, enabled = observation?.canLimit == true || observation?.limited == true,
                    modifier = Modifier.onPreviewKeyEvent(::buttonUp)) {
                    Text(if (observation?.limited == true) "720p LIMIT ✓" else "LIMIT TO 720p", color = healthInk)
                }
                TextButton(onClick = onClose, modifier = Modifier.focusProperties { canFocus = true }
                    .focusRequester(closeFocus).onPreviewKeyEvent(::buttonUp)
                    .background(Color(0xFF4F2678), RoundedCornerShape(16.dp))) {
                    Text("CLOSE", color = Color.White)
                }
            }
            LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
        }
    )
}

@Composable
private fun HealthField(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(label, color = healthInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(value, color = healthMuted, fontSize = 13.sp)
    }
}
