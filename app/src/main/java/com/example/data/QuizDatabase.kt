package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, Quiz::class, Question::class, QuizAttempt::class, QuestionAuditLog::class], version = 20, exportSchema = false)
abstract class QuizDatabase : RoomDatabase() {

    abstract fun quizDao(): QuizDao

    companion object {
        @Volatile
        private var INSTANCE: QuizDatabase? = null

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safe no-op schema migration from 18 to 19
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safe no-op schema migration from 19 to 20
            }
        }

        private val MIGRATION_18_20 = object : Migration(18, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safe no-op schema migration from 18 to 20
            }
        }

        fun getDatabase(context: Context): QuizDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuizDatabase::class.java,
                    "quiz_database"
                )
                .addMigrations(MIGRATION_18_19, MIGRATION_19_20, MIGRATION_18_20)
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
