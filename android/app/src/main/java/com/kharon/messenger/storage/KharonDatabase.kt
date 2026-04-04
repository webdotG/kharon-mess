package com.kharon.messenger.storage

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kharon.messenger.model.ReceptionMode
import kotlinx.coroutines.flow.Flow

// ─── Entity ───────────────────────────────────────────────────────────────────

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val pubKey:        String,
    val name:          String,
    val addedAt:       Long   = System.currentTimeMillis(),
    val receptionMode: String = ReceptionMode.LIVE.name,
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

    @Query("UPDATE contacts SET receptionMode = :mode WHERE pubKey = :pubKey")
    suspend fun updateReceptionMode(pubKey: String, mode: String)
}

// ─── Migrations ───────────────────────────────────────────────────────────────

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE contacts ADD COLUMN receptionMode TEXT NOT NULL DEFAULT 'LIVE'"
        )
    }
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(entities = [ContactEntity::class], version = 2, exportSchema = false)
abstract class KharonDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}