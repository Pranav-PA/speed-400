package dev.pranav.speed400garage

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.pranav.speed400garage.data.seed.SeedLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GarageApplication : Application() {

    @Inject lateinit var seedLoader: SeedLoader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch { seedLoader.seedIfNeeded() }
    }
}
