package com.kharon.messenger.di

import android.content.Context
import androidx.room.Room
import com.kharon.messenger.network.SocketConfig
import com.kharon.messenger.storage.KharonDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton
import com.kharon.messenger.storage.MIGRATION_1_2

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): KharonDatabase {
        val passphrase = SQLiteDatabase.getBytes("kharon_db_key".toCharArray())
        val factory    = SupportFactory(passphrase)

        return Room.databaseBuilder(ctx, KharonDatabase::class.java, "kharon.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    @Singleton
    fun provideContactDao(db: KharonDatabase) = db.contactDao()

    @Provides
    @Singleton
    fun provideSocketConfig(): SocketConfig = SocketConfig(
        serverUrl = "wss://kharon-messenger.duckdns.org"
    )
}
