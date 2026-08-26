package com.byd.wjx.service

import android.os.Build

internal object UpdateInstallPermissionPolicy {
    fun requiresUnknownSourceGrant(sdkInt: Int, canRequestPackageInstalls: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.O && !canRequestPackageInstalls
}
