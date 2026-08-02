package com.kexxdumb.fbnotifier

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FacebookProfile(
    val id: String,
    val label: String,
    val cookieString: String,
)

// Todo se guarda en SharedPreferences en texto plano (igual de privado que
// cualquier dato de la app: solo accesible por esta misma app en el
// dispositivo). Si en algún momento quieres cifrado adicional, se puede
// migrar a EncryptedSharedPreferences de androidx.security sin cambiar la API.
object ProfileStore {
    private const val PREFS = "fb_notifier_prefs"
    private const val PROFILES_KEY = "profiles_v1"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(context: Context): List<FacebookProfile> {
        val raw = prefs(context).getString(PROFILES_KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FacebookProfile(o.getString("id"), o.getString("label"), o.getString("cookieString"))
        }
    }

    private fun save(context: Context, profiles: List<FacebookProfile>) {
        val arr = JSONArray()
        profiles.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("label", it.label)
            o.put("cookieString", it.cookieString)
            arr.put(o)
        }
        prefs(context).edit().putString(PROFILES_KEY, arr.toString()).apply()
    }

    fun add(context: Context, label: String, cookieString: String): FacebookProfile {
        val current = list(context)
        val profile = FacebookProfile(
            id = "p_${System.currentTimeMillis()}_${(1000..9999).random()}",
            label = label.ifBlank { "Cuenta ${current.size + 1}" },
            cookieString = cookieString,
        )
        save(context, current + profile)
        return profile
    }

    fun rename(context: Context, id: String, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        save(context, list(context).map { if (it.id == id) it.copy(label = trimmed) else it })
    }

    fun remove(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
        prefs(context).edit()
            .remove("counts:$id")
            .remove("seen:$id")
            .apply()
    }

    // ---- estado de contadores/deduplicación por perfil ----

    fun getCounts(context: Context, profileId: String): Map<String, Int> {
        val raw = prefs(context).getString("counts:$profileId", "{}") ?: "{}"
        val o = JSONObject(raw)
        return o.keys().asSequence().associateWith { o.getInt(it) }
    }

    fun setCounts(context: Context, profileId: String, counts: Map<String, Int>) {
        val o = JSONObject()
        counts.forEach { (k, v) -> o.put(k, v) }
        prefs(context).edit().putString("counts:$profileId", o.toString()).apply()
    }

    fun getSeen(context: Context, profileId: String): Set<String> {
        val raw = prefs(context).getString("seen:$profileId", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun setSeen(context: Context, profileId: String, seen: Set<String>) {
        val trimmed = seen.toList().takeLast(100)
        val arr = JSONArray()
        trimmed.forEach { arr.put(it) }
        prefs(context).edit().putString("seen:$profileId", arr.toString()).apply()
    }
}
