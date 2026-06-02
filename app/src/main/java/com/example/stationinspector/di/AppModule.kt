package com.example.stationinspector.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.example.stationinspector.data.repository.MapyCzRepository
import com.example.stationinspector.data.repository.MapyCzRepositoryImpl
import com.example.stationinspector.data.repository.MapyCzApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.example.stationinspector.BuildConfig

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMapyCzApi(): MapyCzApi {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newUrl = originalRequest.url.newBuilder()
                .addQueryParameter("apikey", BuildConfig.MAPY_CZ_API_KEY)
                .build()
            val request = originalRequest.newBuilder()
                .url(newUrl)
                .build()
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Never log request/response bodies in release: the Mapy.cz URL
            // carries ?apikey=... and responses contain coordinates/addresses.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl("https://api.mapy.cz/")
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(MapyCzApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMapyCzRepository(api: MapyCzApi): MapyCzRepository {
        return MapyCzRepositoryImpl(api)
    }
}