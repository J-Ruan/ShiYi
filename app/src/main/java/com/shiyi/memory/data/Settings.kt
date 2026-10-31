package com.shiyi.memory.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.settingsStore by preferencesDataStore("settings")

@Serializable
data class Settings(
    val theme: String = "system",
    val notifications: Boolean = false,
    val pinSalt: String = "",
    val pinHash: String = "",
    val biometric: Boolean = false,
    val lastBackup: Long = 0,
    val backupSize: Long = 0,
    val backupLocation: String = "",
    val failedAttempts: Int = 0,
    val retryAfter: Long = 0,
)

class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    val flow =
        store.data.map { p ->
            Settings(
                p[stringPreferencesKey("theme")] ?: "system",
                p[booleanPreferencesKey("notifications")] ?: false,
                p[stringPreferencesKey("pinSalt")] ?: "",
                p[stringPreferencesKey("pinHash")] ?: "",
                p[booleanPreferencesKey("biometric")] ?: false,
                p[longPreferencesKey("lastBackup")] ?: 0,
                p[longPreferencesKey("backupSize")] ?: 0,
                p[stringPreferencesKey("backupLocation")] ?: "",
                p[intPreferencesKey("failedAttempts")] ?: 0,
                p[longPreferencesKey("retryAfter")] ?: 0,
            )
        }

    suspend fun read() = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val current =
                Settings(
                    p[stringPreferencesKey("theme")] ?: "system",
                    p[booleanPreferencesKey("notifications")] ?: false,
                    p[stringPreferencesKey("pinSalt")] ?: "",
                    p[stringPreferencesKey("pinHash")] ?: "",
                    p[booleanPreferencesKey("biometric")] ?: false,
                    p[longPreferencesKey("lastBackup")] ?: 0,
                    p[longPreferencesKey("backupSize")] ?: 0,
                    p[stringPreferencesKey("backupLocation")] ?: "",
                    p[intPreferencesKey("failedAttempts")] ?: 0,
                    p[longPreferencesKey("retryAfter")] ?: 0,
                )
            val s = transform(current)
            p[stringPreferencesKey("theme")] = s.theme
            p[booleanPreferencesKey("notifications")] = s.notifications
            p[stringPreferencesKey("pinSalt")] = s.pinSalt
            p[stringPreferencesKey("pinHash")] = s.pinHash
            p[booleanPreferencesKey("biometric")] = s.biometric
            p[longPreferencesKey("lastBackup")] = s.lastBackup
            p[longPreferencesKey("backupSize")] = s.backupSize
            p[stringPreferencesKey("backupLocation")] = s.backupLocation
            p[intPreferencesKey("failedAttempts")] = s.failedAttempts
            p[longPreferencesKey("retryAfter")] = s.retryAfter
        }
    }
}
