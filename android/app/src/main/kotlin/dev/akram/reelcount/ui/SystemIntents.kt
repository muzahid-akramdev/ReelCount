package dev.akram.reelcount.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

fun Context.openAccessibilitySettings() {
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

fun Context.openAppDetails() {
    val intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        )
    startActivity(intent)
}

fun Context.ignoresBatteryOptimizations(): Boolean {
    val manager = getSystemService(PowerManager::class.java) ?: return false
    return manager.isIgnoringBatteryOptimizations(packageName)
}

fun Context.requestIgnoreBatteryOptimizations() {
    if (ignoresBatteryOptimizations()) return
    val intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
    startActivity(intent)
}

fun Context.accessibilityEnabled(): Boolean {
    val manager = getSystemService(AccessibilityManager::class.java) ?: return false
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == packageName }
}
