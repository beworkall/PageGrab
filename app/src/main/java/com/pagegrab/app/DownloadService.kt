package com.pagegrab.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

/**
 * Foreground service that drains JobQueue so downloads keep going after you leave the app.
 * Respects Settings: max parallel jobs, Wi-Fi only, retry count.
 */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "pagegrab_downloads"
        const val NOTIF_ID = 42
        const val ACTION_ENQUEUE = "com.pagegrab.app.ENQUEUE"
        const val ACTION_CANCEL = "com.pagegrab.app.CANCEL"
        const val EXTRA_JOB_ID = "job_id"
    }

    private val pool by lazy { Executors.newFixedThreadPool(maxOf(1, Prefs.maxParallel)) }
    private var running = 0

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            JobQueue.cancel(intent.getIntExtra(EXTRA_JOB_ID, -1))
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotif("PageGrab is running", "Watching the queue..."))
        pump()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun wifiOk(): Boolean {
        if (!Prefs.wifiOnly) return true
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun pump() {
        synchronized(this) {
            while (running < maxOf(1, Prefs.maxParallel)) {
                val job = JobQueue.nextQueued() ?: break
                if (!wifiOk()) { updateNotif("WAITING FOR WI-FI..."); break }
                running++
                job.state = JobState.RUNNING
                JobQueue.notifyChanged()
                pool.execute { runJob(job) }
            }
        }
        if (running == 0 && !JobQueue.hasActive()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun runJob(job: Job) {
        try {
            val (ok, msg) = Engine.download(this, job.item) { p, eta ->
                job.progress = p
                job.message = "$p% • ETA ${eta}s"
                updateNotif("${job.item.name} — $p%")
                JobQueue.notifyChanged()
            }
            job.attempts++
            if (ok) {
                job.state = JobState.DONE
                job.message = msg
                History.add(this, job.pageTitle.ifBlank { job.item.name }, job.pageUrl, job.item.type, true)
            } else if (job.attempts <= Prefs.retryCount && job.state != JobState.CANCELLED) {
                job.state = JobState.QUEUED
                job.message = "RETRYING: $msg"
            } else {
                job.state = JobState.FAILED
                job.message = msg
                History.add(this, job.pageTitle.ifBlank { job.item.name }, job.pageUrl, job.item.type, false)
            }
        } catch (e: Throwable) {
            job.state = JobState.FAILED
            job.message = "ERROR: " + (e.message ?: "unknown")
        } finally {
            synchronized(this) { running-- }
            JobQueue.notifyChanged()
            pump()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(text: String) {
        if (!Prefs.progressNotif) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotif("PageGrab downloading", text))
    }
}
