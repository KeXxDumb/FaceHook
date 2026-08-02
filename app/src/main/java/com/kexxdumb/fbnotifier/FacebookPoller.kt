package com.kexxdumb.fbnotifier

import java.net.HttpURLConnection
import java.net.URL

object FacebookPoller {
    const val CATEGORY_FRIENDS = "friends"
    const val CATEGORY_MESSAGES = "messages"
    const val CATEGORY_GROUPS = "groups"
    const val CATEGORY_NOTIFICATIONS = "notifications"

    private val categories = listOf(CATEGORY_FRIENDS, CATEGORY_MESSAGES, CATEGORY_GROUPS, CATEGORY_NOTIFICATIONS)

    private val categoryUrls = mapOf(
        CATEGORY_FRIENDS to "https://m.facebook.com/friends/center/requests/",
        CATEGORY_MESSAGES to "https://m.facebook.com/messages/",
        CATEGORY_GROUPS to "https://m.facebook.com/groups/",
        CATEGORY_NOTIFICATIONS to "https://m.facebook.com/notifications.php",
    )

    private val categoryTitles = mapOf(
        CATEGORY_FRIENDS to "Solicitudes de amistad",
        CATEGORY_MESSAGES to "Mensajes",
        CATEGORY_GROUPS to "Actualizaciones de grupos",
        CATEGORY_NOTIFICATIONS to "Notificaciones",
    )

    private const val BOOKMARKS_URL = "https://m.facebook.com/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val anchorPattern = Regex("<a\\b([^>]*)>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
    private fun attr(attrs: String, name: String): String {
        val m = Regex("$name\\s*=\\s*(['\"])(.*?)\\1", RegexOption.IGNORE_CASE).find(attrs)
        return m?.groupValues?.get(2)?.let(::decodeHtml) ?: ""
    }

    private fun decodeHtml(v: String) = v
        .replace("&nbsp;", " ", true)
        .replace("&amp;", "&", true)
        .replace("&quot;", "\"", true)
        .replace("&#39;", "'")
        .replace("&lt;", "<", true)
        .replace("&gt;", ">", true)

    private fun stripTags(v: String) = decodeHtml(v.replace(Regex("<[^>]*>"), " "))

    private fun parseCount(value: String): Int {
        val m = Regex("\\b(\\d+)\\+?\\b").find(value.replace(",", ""))
        return m?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun categoryFromHref(href: String): String? {
        val h = href.lowercase()
        return when {
            h.contains("/friends/center/requests") || h.contains("/friends/requests") -> CATEGORY_FRIENDS
            h.contains("/messages") -> CATEGORY_MESSAGES
            h.contains("/groups") -> CATEGORY_GROUPS
            h.contains("/notifications.php") || h.contains("/notifications/") -> CATEGORY_NOTIFICATIONS
            else -> null
        }
    }

    fun parseCounts(html: String): Map<String, Int> {
        val counts = categories.associateWith { 0 }.toMutableMap()
        anchorPattern.findAll(html).forEach { match ->
            val attrs = match.groupValues[1]
            val href = attr(attrs, "href")
            val category = categoryFromHref(href) ?: return@forEach
            val text = listOf(attr(attrs, "aria-label"), attr(attrs, "title"), stripTags(match.groupValues[2]))
                .joinToString(" ")
            val count = parseCount(text)
            if (count > (counts[category] ?: 0)) counts[category] = count
        }
        return counts
    }

    fun fetchCounts(cookieString: String): Map<String, Int> {
        val conn = URL(BOOKMARKS_URL).openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", cookieString)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        try {
            if (conn.responseCode !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)
                throw RuntimeException("HTTP ${conn.responseCode}${if (errorBody != null) " — $errorBody" else ""}")
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            return parseCounts(html)
        } finally {
            conn.disconnect()
        }
    }

    data class NotificationItem(val category: String, val count: Int) {
        val id get() = "fb:$category:$count"
        val url get() = categoryUrls.getValue(category)
        val title get() = categoryTitles.getValue(category)
        val body get() = "Tienes $count ${categoryTitles.getValue(category).lowercase()} nueva(s)"
    }

    fun diff(current: Map<String, Int>, previous: Map<String, Int>): List<NotificationItem> =
        categories.mapNotNull { category ->
            val count = current[category] ?: 0
            val prev = previous[category] ?: 0
            if (count > prev) NotificationItem(category, count) else null
        }

    fun hasAuthCookie(cookieString: String) = Regex("(^|;\\s*)c_user=").containsMatchIn(cookieString)

    // Pide el nombre real de la cuenta a Facebook, para no tener que
    // preguntárselo al usuario manualmente.
    fun fetchDisplayName(cookieString: String): String? {
        val conn = URL("https://m.facebook.com/me").openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", cookieString)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        try {
            if (conn.responseCode !in 200..299) return null
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val titleMatch = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(html) ?: return null
            var name = decodeHtml(titleMatch.groupValues[1]).trim()
            name = name.removeSuffix(" | Facebook").removeSuffix(" - Facebook").trim()
            return name.ifBlank { null }
        } finally {
            conn.disconnect()
        }
    }
}
