package com.app.check_inpets.di

import android.content.Context
import com.app.check_inpets.data.local.UserPreferencesDataStore
import com.app.check_inpets.data.repository.FirestorePetCareRepository
import com.app.check_inpets.data.repository.PetCareRepository

class AppContainer private constructor(context: Context) {

    private val appContext = context.applicationContext
    val preferencesDataStore = UserPreferencesDataStore(appContext)
    val repository: PetCareRepository = FirestorePetCareRepository(preferencesDataStore)

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context).also { INSTANCE = it }
            }
        }
    }
}
