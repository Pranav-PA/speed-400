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
import dev.pranav.speed400garage.data.db.dao.EventWriteDao
import dev.pranav.speed400garage.data.db.dao.FactDao
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
import dev.pranav.speed400garage.data.db.entity.FactEntity
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
    ],
    version = 1,
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

    companion object {
        const val NAME = "speed400_garage.db"
    }
}
