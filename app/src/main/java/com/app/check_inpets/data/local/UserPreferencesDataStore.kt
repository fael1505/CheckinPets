package com.app.check_inpets.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "checkin_pets_prefs")

class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val LINKED_CAREGIVER_ID = stringPreferencesKey("linked_caregiver_id")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LOCAL_DB_SNAPSHOT = stringPreferencesKey("local_db_snapshot")
        val LAST_AUTO_OPEN_SLOT_KEY = stringPreferencesKey("last_auto_open_slot_key")
        val LAST_REMINDER_SLOT_KEY = stringPreferencesKey("last_reminder_slot_key")
        val CACHED_PREFERRED_TIME = stringPreferencesKey("cached_preferred_time")
        val SNOOZE_UNTIL_MILLIS = longPreferencesKey("snooze_until_millis")
        val DISMISSED_SLOT_KEY = stringPreferencesKey("dismissed_slot_key")
        val PENDING_RESTART_AFTER_UPDATE = booleanPreferencesKey("pending_restart_after_update")
    }

    val linkedCaregiverId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LINKED_CAREGIVER_ID]
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
    }

    val localDbSnapshotJson: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOCAL_DB_SNAPSHOT]
    }

    val lastAutoOpenSlotKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_AUTO_OPEN_SLOT_KEY]
    }

    val lastReminderSlotKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_REMINDER_SLOT_KEY]
    }

    val cachedPreferredTime: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_PREFERRED_TIME]
    }

    val snoozeUntilMillis: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.SNOOZE_UNTIL_MILLIS]
    }

    val dismissedSlotKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DISMISSED_SLOT_KEY]
    }

    val pendingRestartAfterUpdate: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PENDING_RESTART_AFTER_UPDATE] ?: false
    }

    suspend fun setLinkedCaregiverId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(Keys.LINKED_CAREGIVER_ID)
                prefs.remove(Keys.CACHED_PREFERRED_TIME)
                prefs.remove(Keys.SNOOZE_UNTIL_MILLIS)
                prefs.remove(Keys.DISMISSED_SLOT_KEY)
            } else {
                prefs[Keys.LINKED_CAREGIVER_ID] = id
            }
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun saveLocalDbSnapshot(json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCAL_DB_SNAPSHOT] = json
        }
    }

    suspend fun setLastAutoOpenSlotKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_AUTO_OPEN_SLOT_KEY] = key
        }
    }

    suspend fun setLastReminderSlotKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_REMINDER_SLOT_KEY] = key
        }
    }

    suspend fun setCachedPreferredTime(time: String?) {
        context.dataStore.edit { prefs ->
            if (time.isNullOrBlank()) {
                prefs.remove(Keys.CACHED_PREFERRED_TIME)
            } else {
                prefs[Keys.CACHED_PREFERRED_TIME] = time
            }
        }
    }

    suspend fun setSnoozeUntilMillis(untilMillis: Long?) {
        context.dataStore.edit { prefs ->
            if (untilMillis == null) {
                prefs.remove(Keys.SNOOZE_UNTIL_MILLIS)
            } else {
                prefs[Keys.SNOOZE_UNTIL_MILLIS] = untilMillis
            }
        }
    }

    suspend fun setDismissedSlotKey(key: String?) {
        context.dataStore.edit { prefs ->
            if (key == null) {
                prefs.remove(Keys.DISMISSED_SLOT_KEY)
            } else {
                prefs[Keys.DISMISSED_SLOT_KEY] = key
            }
        }
    }

    suspend fun setPendingRestartAfterUpdate(pending: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PENDING_RESTART_AFTER_UPDATE] = pending
        }
    }

    suspend fun clearTurnPromptKeys() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.LAST_AUTO_OPEN_SLOT_KEY)
            prefs.remove(Keys.LAST_REMINDER_SLOT_KEY)
            prefs.remove(Keys.SNOOZE_UNTIL_MILLIS)
            prefs.remove(Keys.DISMISSED_SLOT_KEY)
        }
    }
}
