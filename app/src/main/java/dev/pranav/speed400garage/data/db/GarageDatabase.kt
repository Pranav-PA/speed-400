package dev.pranav.speed400garage.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.pranav.speed400garage.data.db.dao.AttachmentDao
import dev.pranav.speed400garage.data.db.dao.BikeDao
import dev.pranav.speed400garage.data.db.dao.CaptureInboxDao
import dev.pranav.speed400garage.data.db.dao.ComponentActionDao
import dev.pranav.speed400garage.data.db.dao.ComponentDao
import dev.pranav.speed400garage.data.db.dao.DocumentDao
import dev.pranav.speed400garage.data.db.dao.EventDao
import dev.pranav.speed400garage.data.db.dao.CostDao
import dev.pranav.speed400garage.data.db.dao.EventWriteDao
import dev.pranav.speed400garage.data.db.dao.FuelDao
import dev.pranav.speed400garage.data.db.dao.FactDao
import dev.pranav.speed400garage.data.db.dao.FaultDao
import dev.pranav.speed400garage.data.db.dao.HandbookDao
import dev.pranav.speed400garage.data.db.dao.SearchDao
import dev.pranav.speed400garage.data.db.dao.LineItemDao
import dev.pranav.speed400garage.data.db.dao.OdometerDao
import dev.pranav.speed400garage.data.db.dao.SettingDao
import dev.pranav.speed400garage.data.db.entity.AttachmentEntity
import dev.pranav.speed400garage.data.db.entity.BikeEntity
import dev.pranav.speed400garage.data.db.entity.CaptureInboxEntity
import dev.pranav.speed400garage.data.db.entity.ComponentActionEntity
import dev.pranav.speed400garage.data.db.entity.ComponentEntity
import dev.pranav.speed400garage.data.db.entity.DocumentEntity
import dev.pranav.speed400garage.data.db.entity.EventEntity
import dev.pranav.speed400garage.data.db.entity.EventFts
import dev.pranav.speed400garage.data.db.entity.FactEntity
import dev.pranav.speed400garage.data.db.entity.HandbookChunkEntity
import dev.pranav.speed400garage.data.db.entity.HandbookChunkFts
import dev.pranav.speed400garage.data.db.entity.FaultEntity
import dev.pranav.speed400garage.data.db.entity.FuelEntryEntity
import dev.pranav.speed400garage.data.db.entity.InventoryItemEntity
import dev.pranav.speed400garage.data.db.entity.LineItemEntity
import dev.pranav.speed400garage.data.db.entity.OdometerReadingEntity
import dev.pranav.speed400garage.data.db.entity.ReminderEntity
import dev.pranav.speed400garage.data.db.entity.RideEntity
import dev.pranav.speed400garage.data.db.entity.SettingEntity
import dev.pranav.speed400garage.data.db.entity.VendorEntity

@Database(
    entities = [
        BikeEntity::class,
        EventEntity::class,
        EventFts::class,
        LineItemEntity::class,
        FuelEntryEntity::class,
        OdometerReadingEntity::class,
        ComponentEntity::class,
        ComponentActionEntity::class,
        DocumentEntity::class,
        ReminderEntity::class,
        VendorEntity::class,
        AttachmentEntity::class,
        CaptureInboxEntity::class,
        FaultEntity::class,
        RideEntity::class,
        InventoryItemEntity::class,
        FactEntity::class,
        SettingEntity::class,
        HandbookChunkEntity::class,
        HandbookChunkFts::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun bikeDao(): BikeDao
    abstract fun eventDao(): EventDao
    abstract fun eventWriteDao(): EventWriteDao
    abstract fun lineItemDao(): LineItemDao
    abstract fun odometerDao(): OdometerDao
    abstract fun componentDao(): ComponentDao
    abstract fun componentActionDao(): ComponentActionDao
    abstract fun documentDao(): DocumentDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun captureInboxDao(): CaptureInboxDao
    abstract fun factDao(): FactDao
    abstract fun settingDao(): SettingDao
    abstract fun fuelDao(): FuelDao
    abstract fun costDao(): CostDao
    abstract fun faultDao(): FaultDao
    abstract fun searchDao(): SearchDao
    abstract fun handbookDao(): HandbookDao

    companion object {
        const val NAME = "speed400_garage.db"

        /**
         * Adds the full-text index over events. Written by hand rather than left to a
         * destructive fallback: this database is a ten-year record (§3 P6), and losing
         * it to a schema bump would be the single worst bug this app could have.
         */
        /** Adds the handbook corpus and its full-text index. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `handbook_chunk` (" +
                        "`id` TEXT NOT NULL, `page` INTEGER NOT NULL, `ordinal` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, `section` TEXT, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_handbook_chunk_page` ON `handbook_chunk` (`page`)")
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `handbook_chunk_fts` USING FTS4(" +
                        "`text` TEXT, `section` TEXT, content=`handbook_chunk`)"
                )
            }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `event_fts` USING FTS4(" +
                        "`title` TEXT, `notes` TEXT, content=`event`)"
                )
                // Backfill the index from the rows that already exist.
                db.execSQL("INSERT INTO `event_fts`(`event_fts`) VALUES('rebuild')")
            }
        }
    }
}
