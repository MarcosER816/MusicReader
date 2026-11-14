package com.musicreader.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import com.musicreader.core.model.UiLanguage
import java.util.Locale

/**
 * The app's language (D-006, D-028). On Android 13+ the framework stores and applies the per-app
 * language (and shows it in the system settings, see res/xml/locales_config.xml). On Android
 * 10–12 the choice is kept in a small SharedPreferences file and applied when an activity starts.
 */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    fun tag(language: UiLanguage): String? = when (language) {
        UiLanguage.SYSTEM -> null
        UiLanguage.EN -> "en"
        UiLanguage.ES -> "es"
    }

    /** Switches the app to [language]; the activity is recreated with the new strings. */
    fun apply(activity: Activity, language: UiLanguage) {
        val tag = tag(language)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, tag) }
            activity.recreate()
        }
    }

    /** For Android 10–12: the base context with the chosen language (unchanged on 13+). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
}
