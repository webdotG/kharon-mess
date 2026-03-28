package com.kharon.messenger.di

import android.content.Context
import androidx.room.Room
import com.kharon.messenger.network.SocketConfig
import com.kharon.messenger.network.KharonSocket
import com.kharon.messenger.storage.KharonDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── База данных контактов ────────────────────────────────────────────────
    // SQLCipher шифрует всю БД — ключ генерируется из Android Keystore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): KharonDatabase {
        val passphrase = SQLiteDatabase.getBytes("kharon_db_key".toCharArray())
        val factory    = SupportFactory(passphrase)

        return Room.databaseBuilder(ctx, KharonDatabase::class.java, "kharon.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideContactDao(db: KharonDatabase) = db.contactDao()

    // ─── Сетевой конфиг ───────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSocketConfig(): SocketConfig = SocketConfig(
        serverUrl = "wss://YOUR_SERVER_DOMAIN_OR_IP"
    )
}
