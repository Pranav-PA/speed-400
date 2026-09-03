package dev.pranav.speed400garage.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pranav.speed400garage.data.db.GarageDatabase
import dev.pranav.speed400garage.data.db.Migrations
import dev.pranav.speed400garage.data.db.dao.BikeDao
import dev.pranav.speed400garage.data.db.dao.CaptureInboxDao
import dev.pranav.speed400garage.data.db.dao.ComponentDao
import dev.pranav.speed400garage.data.db.dao.EventDao
import dev.pranav.speed400garage.data.db.dao.CostDao
import dev.pranav.speed400garage.data.db.dao.DocumentDao
import dev.pranav.speed400garage.data.db.dao.FactDao
import dev.pranav.speed400garage.data.db.dao.FaultDao
import dev.pranav.speed400garage.data.db.dao.HandbookDao
import dev.pranav.speed400garage.data.db.dao.SearchDao
import dev.pranav.speed400garage.data.db.dao.FuelDao
import dev.pranav.speed400garage.data.db.dao.OdometerDao
import dev.pranav.speed400garage.ai.HandbookImporter
import dev.pranav.speed400garage.data.backup.BackupManager
import dev.pranav.speed400garage.data.repo.GarageRepository
import dev.pranav.speed400garage.data.seed.SeedLoader
import dev.pranav.speed400garage.update.UpdateChecker
import dev.pranav.speed400garage.update.UpdateInstaller
import dev.pranav.speed400garage.update.UpdateSettings
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GarageDatabase =
        Room.databaseBuilder(context, GarageDatabase::class.java, GarageDatabase.NAME)
            // No fallbackToDestructiveMigration: this is a ten-year record (§3 P6).
            // A missing migration must fail loudly in development, never wipe the device.
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides @Singleton
    fun provideSeedLoader(@ApplicationContext context: Context, db: GarageDatabase): SeedLoader =
        SeedLoader(context, db)

    @Provides @Singleton
    fun provideUpdateSettings(@ApplicationContext context: Context): UpdateSettings = UpdateSettings(context)

    @Provides @Singleton
    fun provideUpdateChecker(settings: UpdateSettings): UpdateChecker = UpdateChecker(settings)

    @Provides @Singleton
    fun provideUpdateInstaller(
        @ApplicationContext context: Context,
        checker: UpdateChecker,
    ): UpdateInstaller = UpdateInstaller(context, checker)

    @Provides @Singleton
    fun provideRepository(db: GarageDatabase): GarageRepository = GarageRepository(db)

    @Provides @Singleton
    fun provideBackupManager(@ApplicationContext context: Context, db: GarageDatabase): BackupManager =
        BackupManager(context, db)

    @Provides @Singleton
    fun provideHandbookImporter(@ApplicationContext context: Context, dao: HandbookDao): HandbookImporter =
        HandbookImporter(context, dao)

    @Provides fun provideBikeDao(db: GarageDatabase): BikeDao = db.bikeDao()
    @Provides fun provideEventDao(db: GarageDatabase): EventDao = db.eventDao()
    @Provides fun provideComponentDao(db: GarageDatabase): ComponentDao = db.componentDao()
    @Provides fun provideFactDao(db: GarageDatabase): FactDao = db.factDao()
    @Provides fun provideOdometerDao(db: GarageDatabase): OdometerDao = db.odometerDao()
    @Provides fun provideCaptureInboxDao(db: GarageDatabase): CaptureInboxDao = db.captureInboxDao()
    @Provides fun provideFuelDao(db: GarageDatabase): FuelDao = db.fuelDao()
    @Provides fun provideCostDao(db: GarageDatabase): CostDao = db.costDao()
    @Provides fun provideDocumentDao(db: GarageDatabase): DocumentDao = db.documentDao()
    @Provides fun provideFaultDao(db: GarageDatabase): FaultDao = db.faultDao()
    @Provides fun provideSearchDao(db: GarageDatabase): SearchDao = db.searchDao()
    @Provides fun provideHandbookDao(db: GarageDatabase): HandbookDao = db.handbookDao()
    @Provides fun provideComponentActionDao(db: GarageDatabase): dev.pranav.speed400garage.data.db.dao.ComponentActionDao = db.componentActionDao()
}
