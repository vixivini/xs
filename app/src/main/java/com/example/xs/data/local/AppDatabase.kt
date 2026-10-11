package com.example.xs.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.xs.data.model.FavoriteTopic
import com.example.xs.data.model.SavedPost
import com.example.xs.data.model.LikedPost

@Database(entities = [FavoriteTopic::class, SavedPost::class, LikedPost::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteTopicDao(): FavoriteTopicDao
    abstract fun savedPostDao(): SavedPostDao
    abstract fun likedPostDao(): LikedPostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xs_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
