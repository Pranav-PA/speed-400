package dev.pranav.speed400garage

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.pranav.speed400garage.notify.Channels
import dev.pranav.speed400garage.notify.ReminderWorker
import dev.pranav.speed400garage.data.seed.SeedLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GarageApplication : Application(), Configuration.Provider {

    @Inject lateinit var seedLoader: SeedLoader

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Channels.register(this)
        scope.launch { seedLoader.seedIfNeeded() }
        ReminderWorker.schedule(this)
    }
}
