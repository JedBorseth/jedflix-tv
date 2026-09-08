package com.jedflix.tv.data.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.jedflix.tv.JedflixTvApp
import com.jedflix.tv.MainActivity

/**
 * PackageInstaller confirmation must start from an Activity. A BroadcastReceiver
 * trampoline is blocked on Android 12+ (and Google TV) after the user leaves the app
 * for unknown-sources settings.
 *
 * After an update the TV may resume this activity instead of [MainActivity]. If the
 * installer extras are missing, forward to the real launcher activity instead of
 * finishing into a blank task (which looks like a failed launch).
 */
class InstallStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handleInstallStatus()
        } catch (error: Exception) {
            Log.e(TAG, "Install status handler failed", error)
            openMain()
        } finally {
            finish()
        }
    }

    private fun handleInstallStatus() {
        if (!intent.hasExtra(PackageInstaller.EXTRA_STATUS)) {
            openMain()
            return
        }
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = confirmIntent(intent)
            if (confirm != null) {
                startActivity(confirm)
            } else {
                openMain()
            }
        }
        (application as? JedflixTvApp)?.appUpdateManager?.onInstallStatus(intent)
    }

    private fun openMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_TASK_ON_HOME,
                ),
        )
    }

    private fun confirmIntent(status: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            status.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            status.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    private companion object {
        const val TAG = "JedflixInstall"
    }
}
