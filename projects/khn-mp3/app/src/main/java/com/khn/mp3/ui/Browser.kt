package com.khn.mp3.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

private val KNOWN_BROWSERS = listOf(
    "com.android.chrome",
    "com.sec.android.app.sbrowser",
    "org.mozilla.firefox",
    "com.microsoft.emmx",
    "com.brave.browser",
    "com.opera.browser",
)

/**
 * Ouvre un lien dans le navigateur, même si une app (ex. YouTube) revendique ce domaine :
 * la bibliothèque audio YouTube n'existe que sur le site web.
 */
fun openInBrowser(context: Context, url: String): Boolean {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val default = pm.resolveActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")),
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName?.takeIf { it != "android" && !it.contains("resolver", ignoreCase = true) }
    val browser = default ?: KNOWN_BROWSERS.firstOrNull { pkg ->
        runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
    }
    if (browser != null) intent.setPackage(browser)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(intent.setPackage(null)) }.isSuccess
    }
}
