package com.sumarize.voiceindo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// exportSchema = true: schema di-commit ke git di folder /app/schemas
// Kalau ganti struktur tabel di versi selanjutnya, tambah kelas Migration_X_Y di sini
@Database(entities = [SummaryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun summaryDao(): SummaryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sumarize_db"
                )
                // Kalau schema berubah tanpa migration yang terdaftar, hapus DB lama
                // (cukup untuk fase dev; ganti dengan addMigrations() setelah ada user nyata)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
