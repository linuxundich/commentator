package de.christophlangner.commentator.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.core.net.SystemConnectivityObserver
import de.christophlangner.commentator.data.remote.WordPressApiProvider
import de.christophlangner.commentator.data.remote.WordPressClientFactory
import de.christophlangner.commentator.data.repository.DefaultAuthRepository
import de.christophlangner.commentator.data.repository.DefaultCommentRepository
import de.christophlangner.commentator.data.repository.DefaultReplyTemplateRepository
import de.christophlangner.commentator.data.repository.DefaultSettingsRepository
import de.christophlangner.commentator.data.repository.DefaultTeamRepository
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.CommentRepository
import de.christophlangner.commentator.domain.repository.ReplyTemplateRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.TeamRepository
import de.christophlangner.commentator.notification.NewCommentSource
import de.christophlangner.commentator.notification.PollingNewCommentSource
import javax.inject.Singleton

/**
 * Bindet die Implementierungen an die Schnittstellen der Domänenschicht.
 *
 * Für Tests wird genau dieses Modul ersetzt; der restliche Code merkt davon
 * nichts.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCommentRepository(impl: DefaultCommentRepository): CommentRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindTeamRepository(impl: DefaultTeamRepository): TeamRepository

    @Binds
    @Singleton
    abstract fun bindReplyTemplateRepository(
        impl: DefaultReplyTemplateRepository,
    ): ReplyTemplateRepository

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(impl: SystemConnectivityObserver): ConnectivityObserver

    @Binds
    @Singleton
    abstract fun bindNewCommentSource(impl: PollingNewCommentSource): NewCommentSource

    @Binds
    @Singleton
    abstract fun bindApiProvider(impl: WordPressClientFactory): WordPressApiProvider
}
