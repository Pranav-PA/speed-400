package dev.pranav.speed400garage.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Channels, one per notification class (§8.2).
 *
 * Separate channels are not decoration: they let the routine reminders be silenced
 * without silencing a warranty deadline, which is the difference between a system
 * that survives contact with real use and one that gets turned off wholesale.
 */
object Channels {
    const val WARRANTY = "warranty"
    const val DOCUMENTS = "documents"
    const val ROUTINE = "routine"
    const val DIGEST = "digest"
    const val STALE = "stale"

    fun register(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(WARRANTY, "Warranty deadlines", NotificationManager.IMPORTANCE_HIGH),
            Triple(DOCUMENTS, "Insurance, PUC and RC", NotificationManager.IMPORTANCE_HIGH),
            Triple(ROUTINE, "Service and maintenance", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(DIGEST, "Weekly digest", NotificationManager.IMPORTANCE_LOW),
            Triple(STALE, "Odometer nudges", NotificationManager.IMPORTANCE_LOW),
        ).forEach { (id, name, importance) ->
            manager.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    description = when (id) {
                        WARRANTY -> "Services the warranty depends on. Deliberately loud."
                        DOCUMENTS -> "Expiry of insurance, PUC and registration."
                        ROUTINE -> "Oil, filters, brake fluid and the rest of the schedule."
                        DIGEST -> "One weekly summary of pre-ride checks."
                        else -> "Asks for an odometer reading when the estimate goes stale."
                    }
                }
            )
        }
    }

    fun enabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
