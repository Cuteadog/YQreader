package com.cuteadog.novelreader

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cuteadog.novelreader.storage.StorageLocationManager
import com.cuteadog.novelreader.ui.theme.ThemeManager

class MyApplication : Application() {
    val dataStore: DataStore<Preferences> by preferencesDataStore(name = "novels")

    override fun onCreate() {
        super.onCreate()
        ThemeManager.ensureInit(this)
        StorageLocationManager.ensureInit(this)
    }
}
