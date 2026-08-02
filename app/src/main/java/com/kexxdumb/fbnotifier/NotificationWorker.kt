package com.kexxdumb.fbnotifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "facebook"
private const val WORK_NAME = "fb-notification-poll"
const val POLL_INTERVAL_MINUTES = 15L // mínimo real permitido por Android

class NotificationWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            pollAllProfiles(applicationContext, seed = false)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

fun schedulePeriodicPolling(context: Context) {
    val request = PeriodicWorkRequestBuilder<NotificationWorker>(POLL_INTERVAL_MINUTES, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}

// Revisa todas las cuentas guardadas. seed=true solo guarda el estado
// actual sin notificar (se usa al agregar una cuenta nueva).
// Versión de diagnóstico: en vez de solo notificar, devuelve los contadores
// reales que se lograron leer de cada perfil (o el error si falló), para
// poder ver qué está pasando de verdad en vez de adivinar.
fun pollAllProfilesDebug(context: Context): List<Pair<String, Result<Map<String, Int>>>> {
    return ProfileStore.list(context).map { profile ->
        val result = try {
            if (!FacebookPoller.hasAuthCookie(profile.cookieString)) {
                Result.failure(Exception("Sin cookie de sesión válida (c_user)"))
            } else {
                Result.success(FacebookPoller.fetchCounts(profile.cookieString))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
        profile.label to result
    }
}

fun pollAllProfiles(context: Context, seed: Boolean): Int {
    var fired = 0
    ProfileStore.list(context).forEach { profile ->
        try {
            fired += pollProfile(context, profile, seed)
        } catch (e: Exception) {
            // un perfil con error no debe tumbar la revisión de los demás
        }
    }
    return fired
}

private fun pollProfile(context: Context, profile: FacebookProfile, seed: Boolean): Int {
    if (!FacebookPoller.hasAuthCookie(profile.cookieString)) return 0

    val counts = FacebookPoller.fetchCounts(profile.cookieString)
    val previous = ProfileStore.getCounts(context, profile.id)

    if (seed) {
        ProfileStore.setCounts(context, profile.id, counts)
        return 0
    }

    val items = FacebookPoller.diff(counts, previous)
    ProfileStore.setCounts(context, profile.id, counts)
    if (items.isEmpty()) return 0

    ensureNotificationChannel(context)
    val seen = ProfileStore.getSeen(context, profile.id).toMutableSet()
    var fired = 0
    val manager = NotificationManagerCompat.from(context)

    items.forEach { item ->
        val dedupeId = "${profile.id}:${item.id}"
        if (seen.contains(dedupeId)) return@forEach

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${profile.label} · ${item.title}")
            .setContentText(item.body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(dedupeId.hashCode(), notification)
        seen.add(dedupeId)
        fired += 1
    }

    ProfileStore.setSeen(context, profile.id, seen)
    return fired
}
