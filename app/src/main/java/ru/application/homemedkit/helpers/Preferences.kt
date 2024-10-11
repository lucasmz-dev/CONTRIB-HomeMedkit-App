package ru.application.homemedkit.helpers

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import ru.application.homemedkit.receivers.AlarmSetter
import java.util.Locale

object Preferences : ViewModel() {

    private lateinit var preferences: SharedPreferences
    lateinit var theme: StateFlow<String>
    lateinit var dynamicColors: StateFlow<Boolean>

    fun getInstance(context: Context) {
        preferences = context.getSharedPreferences("${context.packageName}_preferences", MODE_PRIVATE)
        theme = preferences.getThemeFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), THEMES[0])
        dynamicColors = preferences.getColorsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    }

    fun getSortingOrder() = preferences.getString(KEY_ORDER, SORTING[0]) ?: SORTING[0]
    fun getLastKit() = preferences.getLong(KEY_LAST_KIT, 0L)
    fun getMedCompactView() = preferences.getBoolean(KEY_MED_COMPACT_VIEW, false)
    fun getDownloadNeeded() = preferences.getBoolean(KEY_DOWNLOAD, false)
    fun getCheckExpDate() = preferences.getBoolean(KEY_CHECK_EXP_DATE, false)
    fun getLanguage() = preferences.getString(KEY_LANGUAGE, LANGUAGES[0]) ?: LANGUAGES[0]
    fun getDynamicColors() = preferences.getBoolean(KEY_DYNAMIC_COLOR, false)
    fun setLastKit(kitId: Long?) = preferences.edit().putLong(KEY_LAST_KIT, kitId ?: 0L).apply()
    fun setCheckExpDate(context: Context, check: Boolean) =
        AlarmSetter(context).checkExpiration(check)
            .also { preferences.edit().putBoolean(KEY_CHECK_EXP_DATE, check).apply() }

    fun setLocale(context: Context, locale: String) = preferences.edit()
        .putString(KEY_LANGUAGE, locale).apply().also {
            changeLanguage(context, locale); (context as Activity).recreate()
        }

    @Suppress("DEPRECATION")
    fun changeLanguage(context: Context, locale: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales = LocaleList.forLanguageTags(locale)
        else {
            val newLocale = Locale(locale)
            Locale.setDefault(newLocale)

            val resources = context.resources
            val configuration = resources.configuration
            configuration.setLocales(LocaleList(newLocale))
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
}

fun SharedPreferences.getThemeFlow(changedKey: String = KEY_APP_THEME) = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (changedKey == key) trySend(getString(key, THEMES[0]) ?: THEMES[0])
    }

    registerOnSharedPreferenceChangeListener(listener)

    if (contains(changedKey)) send(getString(changedKey, THEMES[0]) ?: THEMES[0])

    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.buffer(Channel.UNLIMITED)

fun SharedPreferences.getColorsFlow(changedKey: String = KEY_DYNAMIC_COLOR) = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (changedKey == key) trySend(getBoolean(key, false))
    }

    registerOnSharedPreferenceChangeListener(listener)

    if (contains(changedKey)) send(getBoolean(changedKey, false))

    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.buffer(Channel.UNLIMITED)