package com.kal.hereme.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionState(
    val canDrawOverlay: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val canPostNotifications: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
) {
    /** Battery exemption is strongly recommended but not required to run. */
    val readyToRun: Boolean
        get() = canDrawOverlay && notificationListenerEnabled
}

object Permissions {

    fun read(context: Context): PermissionState = PermissionState(
        canDrawOverlay = Settings.canDrawOverlays(context),
        notificationListenerEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName),
        canPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        },
        ignoringBatteryOptimizations = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false),
    )

    fun overlaySettings(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun notificationListenerSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun batteryOptimisationSettings(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
}
