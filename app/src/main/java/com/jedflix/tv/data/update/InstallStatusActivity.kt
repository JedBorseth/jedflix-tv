package com.jedflix.tv.data.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import com.jedflix.tv.JedflixTvApp

/**
 * PackageInstaller confirmation must start from an Activity. A BroadcastReceiver
 * trampoline is blocked on Android 12+ (and Google TV) after the user leaves the app
 * for unknown-sources settings.
 */
class InstallStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = confirmIntent(intent)
            if (confirm != null) {
                runCatching { startActivity(confirm) }
            }
        }
        (application as? JedflixTvApp)?.appUpdateManager?.onInstallStatus(intent)
        finish()
    }

    private fun confirmIntent(status: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            status.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            status.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }
}
