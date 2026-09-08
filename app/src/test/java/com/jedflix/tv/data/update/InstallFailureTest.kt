package com.jedflix.tv.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallFailureTest {
    @Test
    fun conflictStatusIsSignatureMismatch() {
        assertEquals(
            InstallFailureReason.SignatureMismatch,
            installFailureReason(PackageInstallStatus.FAILURE_CONFLICT, null),
        )
        assertEquals(
            InstallFailureReason.SignatureMismatch,
            installFailureReason(PackageInstallStatus.FAILURE_INCOMPATIBLE, "blocked"),
        )
    }

    @Test
    fun genericFailureStaysGeneric() {
        assertEquals(
            InstallFailureReason.Generic,
            installFailureReason(PackageInstallStatus.FAILURE, "STATUS_FAILURE"),
        )
    }

    @Test
    fun statusMessageDetectsIncompatibleUpdate() {
        assertEquals(
            InstallFailureReason.SignatureMismatch,
            installFailureReason(
                PackageInstallStatus.FAILURE,
                "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match",
            ),
        )
    }

    @Test
    fun pendingUserActionLeavesProgressUnchanged() {
        assertNull(
            installProgressForStatus(PackageInstallStatus.PENDING_USER_ACTION, null),
        )
    }

    @Test
    fun successAndAbortReturnIdle() {
        assertEquals(
            InstallProgress.Idle,
            installProgressForStatus(PackageInstallStatus.SUCCESS, null),
        )
        assertEquals(
            InstallProgress.Idle,
            installProgressForStatus(PackageInstallStatus.FAILURE_ABORTED, null),
        )
    }

    @Test
    fun otherFailuresCarryReason() {
        assertEquals(
            InstallProgress.Failed(InstallFailureReason.SignatureMismatch),
            installProgressForStatus(PackageInstallStatus.FAILURE_CONFLICT, null),
        )
        assertEquals(
            InstallProgress.Failed(InstallFailureReason.Generic),
            installProgressForStatus(PackageInstallStatus.FAILURE, null),
        )
    }

    @Test
    fun hexEncodingUsesUnsignedBytes() {
        assertEquals(
            "6ebef2",
            byteArrayOf(0x6e.toByte(), 0xbe.toByte(), 0xf2.toByte()).toHexLower(),
        )
    }
}
