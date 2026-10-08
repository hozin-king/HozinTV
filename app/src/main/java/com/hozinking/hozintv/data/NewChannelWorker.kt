package com.hozinking.hozintv.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hozinking.hozintv.MainActivity
import com.hozinking.hozintv.R

/**
 * Cek berkala (WorkManager): bandingkan daftar channel dengan cache terakhir.
 * Kalau ada channel baru -> kirim notifikasi, walau aplikasi sedang tidak dibuka.
 */
class NewChannelWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val CHANNEL_ID = "new_channels"
        private const val PREFS = "hozintv"
        private const val KEY_SEEN = "seen_channel_urls"
    }

    override suspend fun doWork(): Result {
        return try {
            val repo = PlaylistRepository(applicationContext)
            val sources = repo.getSources()
            val lastId = repo.getLastSourceId()
            val source = sources.find { it.id == lastId } ?: sources.first()
            val channels = repo.loadChannels(source)
            if (channels.isEmpty()) return Result.success()

            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seen = prefs.getStringSet(KEY_SEEN, null)
            val current = channels.map { it.url }.toSet()

            if (seen == null) {
                // Pertama kali: simpan saja, jangan spam notifikasi
                prefs.edit().putStringSet(KEY_SEEN, current).apply()
                return Result.success()
            }

            val baru = channels.filter { it.url !in seen }
            prefs.edit().putStringSet(KEY_SEEN, current).apply()

            if (baru.isNotEmpty()) {
                notifyNewChannels(baru.map { it.name })
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notifyNewChannels(names: List<String>) {
        createChannel()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (names.size == 1) names.first()
        else "${names.size} channel baru: ${names.take(3).joinToString(", ")}"
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("\uD83D\uDCFA Channel baru di HozinTV")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1001, notif)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Channel Baru",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifikasi saat ada channel/live baru" }
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }
}
