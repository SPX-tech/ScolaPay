package com.scolapay.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.scolapay.mobile.data.api.ApiClient

@HiltAndroidApp
class ScolaPayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.initialize(this)
    }
}
