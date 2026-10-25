package de.handeltheke.handeldesk.data.local

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @RawQuery(observedEntities = [ProductEntity::class])
    suspend fun rawList(query: SupportSQLiteQuery): List<ProductEntity>

    @RawQuery(observedEntities = [ProductEntity::class])
    suspend fun rawCount(query: SupportSQLiteQuery): Long

    @Query("SELECT * FROM products WHERE uuid=:uuid LIMIT 1")
    suspend fun get(uuid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE uuid=:uuid LIMIT 1")
    fun observe(uuid: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE productId=:id LIMIT 1")
    suspend fun getByProductId(id: Int): ProductEntity?

    @Query("SELECT * FROM products WHERE state!='SYNCED' ORDER BY updatedAt DESC")
    fun observeChanged(): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products")
    fun observeTotalCount(): Flow<Long>

    @Query("SELECT COUNT(*) FROM products WHERE state!='SYNCED'")
    fun observeChangedCount(): Flow<Long>

    @Query("SELECT COUNT(*) FROM products WHERE isDraft=1")
    fun observeDraftCount(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(items: List<ProductEntity>)

    @Query("SELECT * FROM products")
    suspend fun all(): List<ProductEntity>

    @Query("DELETE FROM products WHERE uuid=:uuid AND productId IS NULL")
    suspend fun deleteLocalDraft(uuid: String): Int
}

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM manufacturers ORDER BY name COLLATE NOCASE")
    fun observeManufacturers(): Flow<List<ManufacturerEntity>>

    @Query("SELECT * FROM manufacturers ORDER BY name COLLATE NOCASE")
    suspend fun manufacturers(): List<ManufacturerEntity>

    @Query("SELECT * FROM categories WHERE categoryId=:id LIMIT 1")
    suspend fun categoryById(id: Int): CategoryEntity?

    @Query("SELECT * FROM categories WHERE uuid=:uuid LIMIT 1")
    suspend fun categoryByUuid(uuid: String): CategoryEntity?

    @Query("SELECT * FROM manufacturers WHERE manufacturerId=:id LIMIT 1")
    suspend fun manufacturerById(id: Int): ManufacturerEntity?

    @Query("SELECT * FROM manufacturers WHERE uuid=:uuid LIMIT 1")
    suspend fun manufacturerByUuid(uuid: String): ManufacturerEntity?

    @Query("SELECT COALESCE(MIN(categoryId),0) FROM categories")
    suspend fun minCategoryId(): Int

    @Query("SELECT COALESCE(MIN(manufacturerId),0) FROM manufacturers")
    suspend fun minManufacturerId(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putManufacturers(items: List<ManufacturerEntity>)
}

@Dao
interface ChangeDao {
    @Query("SELECT * FROM changes WHERE synced=0 ORDER BY id DESC")
    fun observePending(): Flow<List<ChangeEntity>>

    @Query("SELECT * FROM changes WHERE synced=0 AND included=1 ORDER BY id")
    suspend fun includedPending(): List<ChangeEntity>

    @Query("SELECT * FROM changes WHERE synced=0 AND entityType=:type AND entityUuid=:uuid AND fieldPath=:path AND operationType='set' LIMIT 1")
    suspend fun findSet(type: String, uuid: String, path: String): ChangeEntity?

    @Insert
    suspend fun insert(change: ChangeEntity): Long

    @Update
    suspend fun update(change: ChangeEntity)

    @Query("DELETE FROM changes WHERE id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM changes WHERE synced=0 AND entityType=:type AND entityUuid=:uuid")
    suspend fun deletePendingForEntity(type: String, uuid: String)

    @Query("UPDATE changes SET included=:included WHERE id=:id AND synced=0")
    suspend fun setIncluded(id: Long, included: Boolean)

    @Query("UPDATE changes SET synced=1 WHERE entityType=:type AND entityUuid=:uuid")
    suspend fun markEntitySynced(type: String, uuid: String)

    @Query("SELECT COUNT(*) FROM changes WHERE synced=0")
    fun observeCount(): Flow<Long>

    @Query("SELECT COUNT(*) FROM changes WHERE synced=0 AND entityType=:type AND entityUuid=:uuid")
    suspend fun pendingCount(type: String, uuid: String): Int

    @Query("SELECT * FROM changes WHERE synced=0 ORDER BY id")
    suspend fun pending(): List<ChangeEntity>
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(event: AuditEventEntity)

    @Query("SELECT * FROM audit_events ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<AuditEventEntity>>
}

@Dao
interface WorkQueueDao {
    @Query("SELECT q.* FROM work_queue q ORDER BY position,createdAt")
    fun observeQueue(): Flow<List<WorkQueueEntity>>

    @Query("SELECT COUNT(*) FROM work_queue")
    fun observeCount(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: WorkQueueEntity)

    @Query("DELETE FROM work_queue WHERE productUuid=:uuid")
    suspend fun remove(uuid: String)

    @Query("SELECT COALESCE(MAX(position),0)+1 FROM work_queue")
    suspend fun nextPosition(): Int
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(session: SessionEntity)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE key=:key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: SettingEntity)
}
