package org.acoustixaudio.opiqo.latencytester

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.Alignment
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import org.acoustixaudio.opiqo.latencytester.ui.theme.LocalThemeExtraColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.acoustixaudio.opiqo.latencytester.ui.theme.LatencyTesterTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AudioChecksViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LatencyTesterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LatencyChecksScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun LatencyChecksScreen(viewModel: AudioChecksViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Audio capability checks", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        // Cards for each probe
        ProbeCard(title = "Low-latency (native)", detail = state.lowDetail)
        Spacer(modifier = Modifier.height(8.dp))
        ProbeCard(title = "Exclusive (native)", detail = state.exclusiveDetail)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Pro audio (native)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Supported: ${displayBool(state.proDetail?.supported)}")
                        state.proDetail?.low?.let { low ->
                            Text("Low -> ${displayBool(low.supported)}  sr:${low.sampleRate ?: "-"}  frames:${low.framesPerBurst ?: "-"}")
                        }
                        state.proDetail?.exclusive?.let { ex ->
                            Text("Exclusive -> ${displayBool(ex.supported)}  sr:${ex.sampleRate ?: "-"}  frames:${ex.framesPerBurst ?: "-"}")
                        }
                    }
                    // status icon (animated)
                    val proTint by animateColorAsState(targetValue = statusColor(state.proDetail?.supported))
                    val proScale by animateFloatAsState(targetValue = if (state.proDetail?.supported == true) 1f else 0.95f)
                    Icon(
                        imageVector = statusIcon(state.proDetail?.supported),
                        contentDescription = null,
                        tint = proTint,
                        modifier = Modifier
                            .size(36.dp)
                            .scale(proScale)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // System hints card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("System hints", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("System hint low-latency: ${state.systemLowLatency}")
                Text("Output frames per buffer: ${state.framesPerBuffer ?: "unknown"}")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.runChecks() }, enabled = !state.running) {
                if (state.running) {
                    Text("Running...")
                } else {
                    Text("Run Checks")
                }
            }

            OutlinedButton(onClick = { copyDiagnostics(context, state) }) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy")
            }

            OutlinedButton(onClick = { shareDiagnostics(context, state) }) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Logs:")
        Text(state.logs ?: "(no logs)")
    }
}

@Composable
fun ProbeCard(title: String, detail: ProbeDetail?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Supported: ${displayBool(detail?.supported)}")
                Text("sr: ${detail?.sampleRate ?: "-"}  frames: ${detail?.framesPerBurst ?: "-"}")
            }
                            val tint by animateColorAsState(targetValue = statusColor(detail?.supported))
                            val scaleVal by animateFloatAsState(targetValue = if (detail?.supported == true) 1f else 0.95f)
                            Icon(
                                imageVector = statusIcon(detail?.supported),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(scaleVal)
                            )
        }
    }
}

@Composable
fun statusColor(v: Boolean?): Color {
    val extras = LocalThemeExtraColors.current
    return when (v) {
        true -> extras.success
        false -> MaterialTheme.colorScheme.error
        null -> extras.warning
    }
}

fun statusIcon(v: Boolean?): androidx.compose.ui.graphics.vector.ImageVector = when (v) {
    true -> Icons.Default.CheckCircle
    false -> Icons.Default.Error
    null -> Icons.Default.Info
}

private fun shareDiagnostics(context: Context, state: AudioChecksUiState) {
    val diagnostics = buildDiagnosticsString(state)
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, diagnostics)
        type = "text/plain"
    }
    val chooser = Intent.createChooser(sendIntent, "Share audio diagnostics")
    context.startActivity(chooser)
}

private fun buildDiagnosticsString(state: AudioChecksUiState): String {
    return buildString {
        append("Low-latency (native): ${displayBool(state.lowDetail?.supported)}\n")
        append("  sampleRate: ${state.lowDetail?.sampleRate ?: "-"}\n")
        append("  framesPerBurst: ${state.lowDetail?.framesPerBurst ?: "-"}\n")
        append("Exclusive (native): ${displayBool(state.exclusiveDetail?.supported)}\n")
        append("  sampleRate: ${state.exclusiveDetail?.sampleRate ?: "-"}\n")
        append("  framesPerBurst: ${state.exclusiveDetail?.framesPerBurst ?: "-"}\n")
        append("Pro audio (native): ${displayBool(state.proDetail?.supported)}\n")
        append("  low: supported=${displayBool(state.proDetail?.low?.supported)}, sr=${state.proDetail?.low?.sampleRate ?: "-"}, frames=${state.proDetail?.low?.framesPerBurst ?: "-"}\n")
        append("  exclusive: supported=${displayBool(state.proDetail?.exclusive?.supported)}, sr=${state.proDetail?.exclusive?.sampleRate ?: "-"}, frames=${state.proDetail?.exclusive?.framesPerBurst ?: "-"}\n")
        append("System low-latency hint: ${state.systemLowLatency}\n")
        append("Frames per buffer: ${state.framesPerBuffer ?: "unknown"}\n")
        append("Logs:\n${state.logs ?: "(none)"}\n")
    }
}

private fun displayBool(v: Boolean?): String = when (v) {
    true -> "Yes"
    false -> "No"
    null -> "Unknown"
}

private fun copyDiagnostics(context: Context, state: AudioChecksUiState) {
    val diagnostics = buildString {
        append("Low-latency (native): ${displayBool(state.lowDetail?.supported)}\n")
        append("  sampleRate: ${state.lowDetail?.sampleRate ?: "-"}\n")
        append("  framesPerBurst: ${state.lowDetail?.framesPerBurst ?: "-"}\n")
        append("Exclusive (native): ${displayBool(state.exclusiveDetail?.supported)}\n")
        append("  sampleRate: ${state.exclusiveDetail?.sampleRate ?: "-"}\n")
        append("  framesPerBurst: ${state.exclusiveDetail?.framesPerBurst ?: "-"}\n")
        append("Pro audio (native): ${displayBool(state.proDetail?.supported)}\n")
        append("  low: supported=${displayBool(state.proDetail?.low?.supported)}, sr=${state.proDetail?.low?.sampleRate ?: "-"}, frames=${state.proDetail?.low?.framesPerBurst ?: "-"}\n")
        append("  exclusive: supported=${displayBool(state.proDetail?.exclusive?.supported)}, sr=${state.proDetail?.exclusive?.sampleRate ?: "-"}, frames=${state.proDetail?.exclusive?.framesPerBurst ?: "-"}\n")
        append("System low-latency hint: ${state.systemLowLatency}\n")
        append("Frames per buffer: ${state.framesPerBuffer ?: "unknown"}\n")
        append("Logs:\n${state.logs ?: "(none)"}\n")
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("audio-diagnostics", diagnostics)
    clipboard.setPrimaryClip(clip)
}

@Preview(showBackground = true)
@Composable
fun LatencyChecksPreview() {
    LatencyTesterTheme {
        // preview with placeholder state (Application() is safe for preview)
        val vm = AudioChecksViewModel(android.app.Application())
        LatencyChecksScreen(viewModel = vm)
    }
}