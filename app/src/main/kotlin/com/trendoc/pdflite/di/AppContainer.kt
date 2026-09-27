package com.trendoc.pdflite.di

import android.app.Application
import android.content.Context
import com.trendoc.pdflite.TrenDocApplication
import com.trendoc.pdflite.appearance.AppearanceRepository
import com.trendoc.pdflite.billing.EntitlementRepository
import com.trendoc.pdflite.onboarding.OnboardingRepository
import com.trendoc.pdflite.recents.RecentsRepository

/**
 * The app's one place for building long-lived dependencies — a manual service locator, kept
 * deliberately small rather than adopting Hilt for a handful of repositories.
 *
 * ViewModels take what they need as constructor parameters that *default* to these (see e.g.
 * [com.trendoc.pdflite.ui.recents.RecentsViewModel]), so production code is unchanged while a
 * test can pass its own. Composables read the same instances through [appContainer] rather than
 * constructing their own in `remember {}`.
 *
 * Play Billing and AdMob wrappers are not held here: each owns a live client connection whose
 * lifetime is its screen's ViewModel, and they are built there from [entitlements].
 */
class AppContainer(application: Application) {
    val recents: RecentsRepository by lazy { RecentsRepository(application) }
    val entitlements: EntitlementRepository by lazy { EntitlementRepository(application) }
    val appearance: AppearanceRepository by lazy { AppearanceRepository(application) }
    val onboarding: OnboardingRepository by lazy { OnboardingRepository(application) }
}

/** The container, from any Context in the app. */
val Context.appContainer: AppContainer
    get() = (applicationContext as TrenDocApplication).container
