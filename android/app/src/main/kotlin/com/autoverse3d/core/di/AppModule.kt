package com.autoverse3d.core.di

import android.content.Context
import androidx.room.Room
import com.autoverse3d.BuildConfig
import com.autoverse3d.core.database.AppDatabase
import com.autoverse3d.core.network.AutoVerseApi
import com.autoverse3d.data.ContentRepository
import com.autoverse3d.data.ContentSource
import com.autoverse3d.domain.ContentParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "autoverse.db").build()

    @Provides fun configurationDao(db: AppDatabase) = db.configurations()
    @Provides fun progressDao(db: AppDatabase) = db.progress()
    @Provides fun packDao(db: AppDatabase) = db.packs()

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun api(client: OkHttpClient): AutoVerseApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(ContentParser.json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AutoVerseApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun contentSource(impl: ContentRepository): ContentSource
}
