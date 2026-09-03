package dev.pranav.speed400garage.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pranav.speed400garage.MainActivity
import dev.pranav.speed400garage.R
import dev.pranav.speed400garage.data.db.GarageDatabase
import dev.pranav.speed400garage.data.repo.GarageRepository
import dev.pranav.speed400garage.domain.engine.DueComponent
import dev.pranav.speed400garage.domain.engine.DueDocument
import dev.pranav.speed400garage.domain.engine.DueEngine
import dev.pranav.speed400garage.domain.engine.DueItem
import dev.pranav.speed400garage.domain.engine.NotifyClass
import dev.pranav.speed400garage.domain.engine.OdometerProjector
import dev.pranav.speed400garage.domain.engine.Severity
import java.util.concurrent.TimeUnit

/**
 * The daily recompute (§8.2).
 *
 * Deliberately conservative about what it is allowed to say out loud:
 *
 *  - Light checks never notify individually. They are collapsed into one weekly
 *    digest, because they are exactly the items that would otherwise train you to
 *    swipe everything away — and a reminder system you swipe away is worse than none.
 *  - Nothing below DUE_SOON is announced at all.
 *  - A km-based date built on a stale odometer estimate says so in the text rather
 *    than presenting itself as a deadline.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repo: GarageRepository,
    private val db: GarageDatabase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bikeId = repo.activeBikeId() ?: return Result.success()
        Channels.register(context)

        val today = repo.today()
        val projection = OdometerProjector.project(repo.readings(bikeId), today)

        val items = DueEngine.compute(
            components = componentsFor(),
            baselines = repo.baselines(bikeId),
            bikeStart = repo.bikeStart(bikeId),
            documents = db.documentDao().all().map {
                DueDocument(
                    id = it.id,
                    label = it.docType.replaceFirstChar { c -> c.uppercase() },
                    expiresOnDay = it.expiresOn?.let { e -> repo.epochDayOf(e) },
                    secondaryExpiresOnDay = it.secondaryExpiresOn?.let { e -> repo.epochDayOf(e) },
                    secondaryLabel = if (it.docType == "insurance") "Insurance — third-party cover" else null,
                )
            },
            projection = projection,
            today = today,
        )

        if (!canNotify()) return Result.success()

        // Individually: only the loud classes, and only once they actually matter.
        items.filter { it.notifyClass != NotifyClass.DIGEST && it.severity >= Severity.DUE_SOON }
            .take(MAX_INDIVIDUAL)
            .forEach { notifyItem(it) }

        // Once a week, everything else in one line each.
        if (today % 7L == 0L) {
            val digest = items.filter { it.notifyClass == NotifyClass.DIGEST }
            if (digest.isNotEmpty()) notifyDigest(digest)
        }

        DueEngine.stalenessNudge(projection, today)
            ?.takeIf { (projection?.daysSinceReading ?: 0) >= STALE_NOTIFY_DAYS }
            ?.let { notifyStale(it) }

        return Result.success()
    }

    private suspend fun componentsFor(): List<DueComponent> =
        db.componentDao().allOnce().map {
            DueComponent(
                key = it.key, label = it.displayName,
                intervalKm = it.intervalKm, intervalDays = it.intervalDays,
                intervalSource = it.intervalSource, manualPageRef = it.manualPageRef,
                isWarrantyRelevant = it.isWarrantyRelevant, isDailyCheck = it.isDailyCheck,
                isOneOff = it.isOneOff, firstDueKm = it.firstDueKm,
            )
        }

    private fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && Channels.enabled(context)

    private fun notifyItem(item: DueItem) {
        val channel = when (item.notifyClass) {
            NotifyClass.WARRANTY -> Channels.WARRANTY
            NotifyClass.DOCUMENT -> Channels.DOCUMENTS
            else -> Channels.ROUTINE
        }
        val text = buildString {
            when {
                item.severity == Severity.OVERDUE -> append("Overdue")
                item.kmRemaining != null && item.daysRemaining != null ->
                    append("${item.kmRemaining} km or ${item.daysRemaining} days away")
                item.daysRemaining != null -> append("${item.daysRemaining} days away")
                item.kmRemaining != null -> append("${item.kmRemaining} km away")
                else -> append("Due")
            }
            if (item.isStale) append(" — based on a stale odometer estimate")
            if (item.isWarrantyRelevant) append(". Keep the invoice for warranty.")
        }
        post(item.key.hashCode(), channel, item.label, text)
    }

    private fun notifyDigest(items: List<DueItem>) {
        post(
            DIGEST_ID, Channels.DIGEST,
            "Weekly checks",
            items.joinToString(" · ") { it.label },
        )
    }

    private fun notifyStale(message: String) = post(STALE_ID, Channels.STALE, "Odometer", message)

    private fun post(id: Int, channel: String, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object {
        private const val NAME = "reminder-recompute"
        private const val MAX_INDIVIDUAL = 3
        private const val DIGEST_ID = 900_001
        private const val STALE_ID = 900_002
        private const val STALE_NOTIFY_DAYS = 30

        /** One daily recompute; the UI recomputes on every write regardless (§8.2). */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }
    }
}
