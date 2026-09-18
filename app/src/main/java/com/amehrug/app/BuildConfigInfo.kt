package com.amehrug.app

import android.content.Context
import android.os.Build

object BuildConfigInfo {
    @Volatile
    var VERSION: String = "unknown"
        private set

    fun init(context: Context) {
        VERSION = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun header(): List<String> = listOf(
        "Amehrug $VERSION",
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "This log holds no note content.",
    )
}
