package com.ios25pan.launcher.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

/** 设置项（DataStore）。当前只记录用户从桌面移除过的元素，避免它被"自动补齐"又出现。 */
@Singleton
class LauncherPrefs @Inject constructor(@ApplicationContext private val context: Context) {

    private val hiddenKey = stringSetPreferencesKey("hidden_actions")

    val hiddenActions: Flow<Set<String>> =
        context.launcherDataStore.data.map { it[hiddenKey] ?: emptySet() }

    suspend fun hide(action: String) {
        context.launcherDataStore.edit { p -> p[hiddenKey] = (p[hiddenKey] ?: emptySet()) + action }
    }
}
