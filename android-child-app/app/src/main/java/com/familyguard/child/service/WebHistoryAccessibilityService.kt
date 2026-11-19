package com.familyguard.child.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.familyguard.child.data.AppDatabase
import com.familyguard.child.data.model.WebVisitRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads ONLY the browser address bar to log which websites were visited.
 * It never reads page body text, form fields, or passwords.
 *
 * Address-bar view IDs vary per browser; we try the known IDs first, then
 * fall back to scanning visible nodes for a URL-shaped string.
 */
class WebHistoryAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Known address-bar resource IDs per browser.
    private val addressBarIds = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
        "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
        "com.chrome.dev" to listOf("com.chrome.dev:id/url_bar"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
        "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
        "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
        "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
        "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput")
    )

    // Per-browser de-dupe of the last-seen URL.
    private val lastSeen = HashMap<String, Pair<String, Long>>()
    private val dedupeWindowMs = 4000L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (!addressBarIds.containsKey(pkg)) return

        val root = rootInActiveWindow ?: return
        val url = extractUrl(root, pkg) ?: return
        val normalized = normalize(url) ?: return

        val now = System.currentTimeMillis()
        val prev = lastSeen[pkg]
        if (prev != null && prev.first == normalized && now - prev.second < dedupeWindowMs) return
        lastSeen[pkg] = normalized to now

        val host = hostOf(normalized)
        scope.launch {
            val dao = AppDatabase.get(applicationContext).webVisitDao()
            // Skip if identical to the immediately previous stored row for this browser.
            val last = dao.lastForBrowser(pkg)
            if (last?.url == normalized && now - last.timestamp < dedupeWindowMs) return@launch
            dao.insert(
                WebVisitRecord(
                    timestamp = now,
                    url = normalized,
                    host = host,
                    browserPackage = pkg
                )
            )
        }
    }

    private fun extractUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        // 1) Try known address-bar IDs.
        addressBarIds[pkg]?.forEach { id ->
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            nodes?.firstOrNull()?.text?.toString()?.let { if (it.isNotBlank()) return it }
        }
        // 2) Fallback: scan a shallow slice of the tree for a URL-shaped string.
        return scanForUrl(root, depth = 0)
    }

    private fun scanForUrl(node: AccessibilityNodeInfo?, depth: Int): String? {
        node ?: return null
        if (depth > 6) return null
        val text = node.text?.toString()
        if (text != null && looksLikeUrl(text)) return text
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanForUrl(child, depth + 1)?.let { return it }
        }
        return null
    }

    private fun looksLikeUrl(s: String): Boolean {
        val t = s.trim()
        if (t.contains(' ')) return false
        if (t.startsWith("http://") || t.startsWith("https://")) return true
        // host-only like "example.com/..." — require a dot and a plausible TLD.
        return Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$").matches(t)
    }

    /** Return a canonical https URL or null if it isn't a real address. */
    private fun normalize(raw: String): String? {
        var t = raw.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "https://$t"
        return try {
            val u = java.net.URI(t)
            if (u.host.isNullOrBlank()) null else t
        } catch (e: Exception) {
            null
        }
    }

    private fun hostOf(url: String): String = try {
        java.net.URI(url).host ?: url
    } catch (e: Exception) {
        url
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
