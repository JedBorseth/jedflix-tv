package com.jedflix.tv.data.update

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.jedflix.tv.MainActivity

/**
 * Google TV may keep resuming this component after a PackageInstaller self-update, even
 * for older APKs that never declared it. Always hand off to [MainActivity] so a stale
 * launcher entry cannot look like a crash.
 */
class InstallStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { ApkInstaller(this).abandonStaleSessions() }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_TASK_ON_HOME,
            ),
        )
        finish()
    }
}
