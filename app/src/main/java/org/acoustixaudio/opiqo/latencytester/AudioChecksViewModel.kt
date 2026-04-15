package org.acoustixaudio.opiqo.latencytester

import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ProbeDetail(
    val supported: Boolean? = null,
    val sampleRate: Int? = null,
    val framesPerBurst: Int? = null,
    val resultCode: Int? = null,
    val error: String? = null
)

data class ProCombined(
    val supported: Boolean? = null,
    val low: ProbeDetail? = null,
    val exclusive: ProbeDetail? = null
)

data class AudioChecksUiState(
    val lowDetail: ProbeDetail? = null,
    val exclusiveDetail: ProbeDetail? = null,
    val proDetail: ProCombined? = null,
    val deviceMmapPolicy: String? = null,
    val systemLowLatency: Boolean = false,
    val framesPerBuffer: String? = null,
    val running: Boolean = false,
    val logs: String? = null
)

class AudioChecksViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AudioChecksUiState())
    val uiState: StateFlow<AudioChecksUiState> = _uiState

    fun runChecks() {
        // Avoid concurrent runs
        if (_uiState.value.running) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(running = true, logs = "")

            val app = getApplication<Application>()
            val pm = app.packageManager
            val systemLow = try {
                pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
            } catch (t: Throwable) {
                false
            }

            val am = app.getSystemService(Application.AUDIO_SERVICE) as? AudioManager
            val frames = try {
                am?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            } catch (t: Throwable) {
                null
            }

            val logs = StringBuilder()
            logs.append("System low-latency: $systemLow\n")
            logs.append("Frames per buffer: ${frames ?: "unknown"}\n")

            // Helper to parse JSON returned from native probes
            fun parseProbe(jsonStr: String?): ProbeDetail? {
                if (jsonStr == null) return null
                try {
                    val obj = org.json.JSONObject(jsonStr)
                    val supported = if (obj.has("supported")) obj.optBoolean("supported") else null
                    val sampleRate = if (obj.has("sampleRate")) {
                        val v = obj.optInt("sampleRate")
                        if (v == 0 && obj.isNull("sampleRate")) null else v
                    } else null
                    val frames = if (obj.has("framesPerBurst")) {
                        val v = obj.optInt("framesPerBurst")
                        if (v == 0 && obj.isNull("framesPerBurst")) null else v
                    } else null
                    val resultCode = if (obj.has("resultCode")) obj.optInt("resultCode") else null
                    val error = if (obj.has("error")) obj.optString("error") else null
                    return ProbeDetail(supported = supported, sampleRate = sampleRate, framesPerBurst = frames, resultCode = resultCode, error = error)
                } catch (t: Throwable) {
                    logs.append("JSON parse error: $t\n")
                    return null
                }
            }

            // Run native probes on IO dispatcher with simple timeouts and parse JSON
            val lowDetail = withContext(Dispatchers.IO) {
                withTimeoutOrNull(3000) {
                    try {
                        val s = AudioChecker.checkLowLatencyJson()
                        logs.append("Native low-latency probe JSON: ${s ?: "(null)"}\n")
                        parseProbe(s)
                    } catch (t: Throwable) {
                        logs.append("Native low-latency probe exception: $t\n")
                        null
                    }
                }
            }

            // Device-level report (oboe + aaudio properties)
            val deviceReport = withContext(Dispatchers.IO) {
                withTimeoutOrNull(2000) {
                    try {
                        val s = AudioChecker.getDeviceReportJson()
                        logs.append("Device report JSON: ${s ?: "(null)"}\n")
                        s
                    } catch (t: Throwable) {
                        logs.append("Device report exception: $t\n")
                        null
                    }
                }
            }
            var mmapPolicy: String? = null
            if (deviceReport != null) {
                try {
                    val obj = org.json.JSONObject(deviceReport)
                    if (obj.has("aaudio_mmap_policy")) {
                        mmapPolicy = obj.optString("aaudio_mmap_policy")
                    }
                } catch (t: Throwable) {
                    logs.append("Device report parse error: $t\n")
                }
            }

            val exclDetail = withContext(Dispatchers.IO) {
                withTimeoutOrNull(3000) {
                    try {
                        val s = AudioChecker.checkExclusiveJson()
                        logs.append("Native exclusive probe JSON: ${s ?: "(null)"}\n")
                        parseProbe(s)
                    } catch (t: Throwable) {
                        logs.append("Native exclusive probe exception: $t\n")
                        null
                    }
                }
            }

            val proDetail = withContext(Dispatchers.IO) {
                withTimeoutOrNull(3000) {
                    try {
                        val s = AudioChecker.checkProAudioJson()
                        logs.append("Native pro-audio probe JSON: ${s ?: "(null)"}\n")
                        if (s == null) return@withTimeoutOrNull null
                        try {
                            val obj = org.json.JSONObject(s)
                            val supported = if (obj.has("supported")) obj.optBoolean("supported") else null
                            val lowObj = if (obj.has("low")) obj.optJSONObject("low") else null
                            val exclObj = if (obj.has("exclusive")) obj.optJSONObject("exclusive") else null
                            val lowDetailParsed = lowObj?.let { parseProbe(it.toString()) }
                            val exclDetailParsed = exclObj?.let { parseProbe(it.toString()) }
                            ProCombined(supported = supported, low = lowDetailParsed, exclusive = exclDetailParsed)
                        } catch (t: Throwable) {
                            logs.append("Pro JSON parse error: $t\n")
                            null
                        }
                    } catch (t: Throwable) {
                        logs.append("Native pro-audio probe exception: $t\n")
                        null
                    }
                }
            }

            _uiState.value = AudioChecksUiState(
                lowDetail = lowDetail,
                exclusiveDetail = exclDetail,
                proDetail = proDetail,
                deviceMmapPolicy = mmapPolicy,
                systemLowLatency = systemLow,
                framesPerBuffer = frames,
                running = false,
                logs = logs.toString()
            )
        }
    }
}

