package com.hereliesaz.illumera.data.trakt

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hereliesaz.illumera.data.profile.ProfileConfigurationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Background Trakt sync: every few hours (on any network) runs the cheap
 * last_activities-gated [TraktSyncManager.checkAndSync] for the last active profile,
 * if that profile has a Trakt token. Dependencies come from a Hilt entry point so the
 * default WorkManager initializer keeps working without a custom worker factory.
 */
class TraktSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun traktSyncManager(): TraktSyncManager
        fun traktAuthManager(): TraktAuthManager
        fun profileConfigurationManager(): ProfileConfigurationManager
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val profiles = deps.profileConfigurationManager()
        val profileId = profiles.getLastActiveProfileId() ?: return Result.success()
        if (deps.traktAuthManager().getAccessToken() == null) return Result.success()
        return try {
            // Hold the profile runtime so a profile switch can't swap history tables mid-sync.
            profiles.withActiveProfileRuntime(profileId) {
                deps.traktSyncManager().checkAndSync()
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Background Trakt sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TraktSyncWorker"
        private const val WORK_NAME = "trakt-periodic-sync"
        private const val INTERVAL_HOURS = 6L

        /** Enqueues the periodic sync; keeps an existing schedule untouched. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TraktSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
