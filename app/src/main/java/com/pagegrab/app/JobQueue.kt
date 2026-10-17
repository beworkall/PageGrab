package com.pagegrab.app

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

enum class JobState { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

data class Job(
    val id: Int,
    val item: Item,
    val pageUrl: String,
    val pageTitle: String,
    val ua: String,
    var state: JobState = JobState.QUEUED,
    var progress: Int = 0,
    var message: String = "",
    var attempts: Int = 0
)

/** In-memory queue the DownloadService drains; the UI (History/queue screen) reads the same list. */
object JobQueue {
    private val counter = AtomicInteger(0)
    val jobs = java.util.Collections.synchronizedList(ArrayList<Job>())
    val listeners = ConcurrentLinkedQueue<() -> Unit>()

    fun add(item: Item, pageUrl: String, pageTitle: String, ua: String): Job {
        val j = Job(counter.incrementAndGet(), item, pageUrl, pageTitle, ua)
        jobs.add(j)
        notifyChanged()
        return j
    }

    fun nextQueued(): Job? = jobs.firstOrNull { it.state == JobState.QUEUED }

    fun cancel(id: Int) {
        jobs.find { it.id == id }?.let {
            if (it.state == JobState.QUEUED || it.state == JobState.RUNNING) it.state = JobState.CANCELLED
        }
        notifyChanged()
    }

    fun retry(id: Int) {
        jobs.find { it.id == id }?.let {
            if (it.state == JobState.FAILED || it.state == JobState.CANCELLED) {
                it.state = JobState.QUEUED
                it.progress = 0
                it.message = ""
            }
        }
        notifyChanged()
    }

    fun clearFinished() {
        jobs.removeAll { it.state == JobState.DONE || it.state == JobState.CANCELLED }
        notifyChanged()
    }

    fun hasActive(): Boolean = jobs.any { it.state == JobState.QUEUED || it.state == JobState.RUNNING }

    fun notifyChanged() { listeners.forEach { it() } }
}
