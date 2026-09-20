package de.christophlangner.commentator.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.christophlangner.commentator.data.local.CommentatorDatabase
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.remote.HttpLogging
import de.christophlangner.commentator.data.remote.HttpsOnlyInterceptor
import de.christophlangner.commentator.data.remote.UserAgentInterceptor
import de.christophlangner.commentator.data.remote.WordPressClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        // WordPress liefert je nach Kontext unterschiedlich viele Felder;
        // fehlende Werte dürfen nicht zum Abbruch führen.
        coerceInputValues = true
        // Nicht gesetzte Felder werden beim Schreiben weggelassen, damit eine
        // Teilaktualisierung nicht versehentlich Felder leert.
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpsOnlyInterceptor)
        .addInterceptor(UserAgentInterceptor(WordPressClientFactory.USER_AGENT))
        .apply { HttpLogging.interceptors().forEach(::addInterceptor) }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("commentator_settings") },
    )

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CommentatorDatabase = Room.databaseBuilder(
        context,
        CommentatorDatabase::class.java,
        CommentatorDatabase.NAME,
    ).build()

    @Provides
    fun provideCommentDao(database: CommentatorDatabase): CommentDao = database.commentDao()
}
