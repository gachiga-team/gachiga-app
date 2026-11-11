package com.gachiga.app.data.trip

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Trip::class, Segment::class, Refuel::class, Expense::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        /** v2: 내비 연동 (spec.md 9절 N1, N2). 기존 기록은 그대로 두고 칸만 더한다. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Trip ADD COLUMN destinationName TEXT")
                db.execSQL("ALTER TABLE Expense ADD COLUMN note TEXT")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gachiga.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
