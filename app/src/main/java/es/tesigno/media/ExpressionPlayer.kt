package es.tesigno.media

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import es.tesigno.data.Expression
import es.tesigno.data.VideoResolver

@OptIn(UnstableApi::class)
@Composable
fun ExpressionPlayer(expression: Expression, backLabel: String = "Volver al catálogo", onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uri = remember(expression.video) {
        MediaFiles(context).resolve(expression.video)
    }
    var position by rememberSaveable { mutableLongStateOf(0L) }
    var resume by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(uri != null) }
    // The player exists only while the screen is started: no background playback or retained decoder.
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) started = true
            if (event == Lifecycle.Event.ON_STOP) started = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val player = remember(uri, started) {
        if (uri == null || !started) null else ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(uri))
            seekTo(position)
            playWhenReady = resume
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && player != null) {
                position = player.currentPosition
                resume = player.playWhenReady && !error
                player.pause()
            }
            if (event == Lifecycle.Event.ON_RESUME && resume) player?.play()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(exception: PlaybackException) { error = true; buffering = false }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }
        }
        player?.addListener(listener)
        player?.prepare()
        onDispose {
            if (player != null) {
                position = player.currentPosition
                player.removeListener(listener)
                player.release()
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(expression.text, Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.headlineLarge)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                uri == null -> Text("Vídeo pendiente de incorporar", style = MaterialTheme.typography.headlineSmall)
                error -> Text("No se ha podido reproducir el vídeo. Pulsa Repetir para intentarlo de nuevo.", style = MaterialTheme.typography.titleLarge)
                player != null -> {
                    AndroidView(factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                        modifier = Modifier.fillMaxSize(), update = { it.player = player }, onRelease = { it.player = null })
                    if (buffering) CircularProgressIndicator()
                }
            }
        }
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                error = false
                player?.seekTo(0)
                player?.prepare()
                player?.play()
            }, enabled = player != null, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Repetir", style = MaterialTheme.typography.titleLarge) }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(backLabel, style = MaterialTheme.typography.titleLarge) }
        }
    }
}
