package com.jedflix.tv.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jedflix.tv.JedflixTvApp

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? JedflixTvApp ?: return
        app.appUpdateManager.onInstallStatus(intent)
    }
}
