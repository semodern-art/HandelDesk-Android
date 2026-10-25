package de.handeltheke.handeldesk.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class, CategoryEntity::class, ManufacturerEntity::class,
        ChangeEntity::class, AuditEventEntity::class, WorkQueueEntity::class,
        SessionEntity::class, SettingEntity::class
    ],
    version = 1,
    exportSchema = true,
)
abstract class HandelDeskDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun references(): ReferenceDao
    abstract fun changes(): ChangeDao
    abstract fun audit(): AuditDao
    abstract fun queue(): WorkQueueDao
    abstract fun sessions(): SessionDao
    abstract fun settings(): SettingDao

    companion object {
        @Volatile private var instance: HandelDeskDatabase? = null

        fun get(context: Context): HandelDeskDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HandelDeskDatabase::class.java,
                "handeldesk.db"
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                .also { instance = it }
        }
    }
}
