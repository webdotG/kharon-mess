package com.kharon.messenger.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entity ───────────────────────────────────────────────────────────────────

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val pubKey:   String,   // уникальный идентификатор — публичный ключ
    val name:     String,   // локальное имя, только на нашем устройстве
    val addedAt:  Long = System.currentTimeMillis(),
)

// ─── DAO ──────────────────────────────────────────────────────────────────────

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllFlow(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE pubKey = :pubKey LIMIT 1")
    suspend fun getByPubKey(pubKey: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE pubKey = :pubKey")
    suspend fun deleteByPubKey(pubKey: String)
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(entities = [ContactEntity::class], version = 1, exportSchema = false)
abstract class KharonDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}
