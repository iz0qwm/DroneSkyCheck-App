package it.droneskycheck.app.data

import android.util.Log

internal object DscLogger {
    fun debug(tag: String, message: String) {
        runCatching {
            Log.i(tag, message)
            if (tag != AppLogTag) Log.i(AppLogTag, "[$tag] $message")
        }
    }

    fun trace(tag: String, message: String) {
        runCatching {
            Log.d(tag, message)
            if (tag != AppLogTag) Log.d(AppLogTag, "[$tag] $message")
        }
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable == null) {
                Log.w(tag, message)
                if (tag != AppLogTag) Log.w(AppLogTag, "[$tag] $message")
            } else {
                Log.w(tag, message, throwable)
                if (tag != AppLogTag) Log.w(AppLogTag, "[$tag] $message", throwable)
            }
        }
    }

    private const val AppLogTag = "DroneSkyMap"
}
