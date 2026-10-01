package com.kynox.gaming.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.kynox.gaming.core.utils.Logger

private const val TAG = "ChargingJobService"
private const val JOB_ID = 4001

/**
 * Wakes the app the moment a charger is connected, even if Kynox's process
 * was killed and nothing was running. `JobInfo.setRequiresCharging(true)`
 * is the documented replacement for listening to `ACTION_POWER_CONNECTED`
 * in a manifest receiver, which Android silently stops delivering once the
 * app targets API 26+ (see README "Known limitations").
 *
 * A charging job fires once and is then done, so [ChargingMonitorService]
 * re-arms a fresh one via [schedule] every time it stops -- once for the
 * charging session that just ended, priming the next one.
 */
class ChargingJobService : android.app.job.JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Logger.i(TAG, "Charging detected, starting monitor")
        ChargingMonitorService.start(applicationContext)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, ChargingJobService::class.java))
                .setRequiresCharging(true)
                .setPersisted(true)
                .build()
            val result = scheduler.schedule(job)
            Logger.d(TAG, "Scheduled charging job: ${if (result == JobScheduler.RESULT_SUCCESS) "ok" else "failed"}")
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
