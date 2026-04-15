package org.acoustixaudio.opiqo.latencytester

class AudioChecker {
    companion object {
        init {
            try {
                System.loadLibrary("audio_checker")
            } catch (t: Throwable) {
                // library may be missing during some IDE operations; log for debugging
                android.util.Log.w("AudioChecker", "Failed to load native lib: $t")
            }
        }

        @JvmStatic
        external fun checkLowLatencyJson(): String?

        @JvmStatic
        external fun checkExclusiveJson(): String?

        @JvmStatic
        external fun checkProAudioJson(): String?

        @JvmStatic
        external fun getDeviceReportJson(): String?
    }
}

