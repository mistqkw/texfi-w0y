package com.texfi.w0y

import android.app.Application
import com.texfi.w0y.data.YouTubeLocaleFix
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class W0yApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // До первого запроса к YouTube: на некоторых телефонах системный
        // языковой тег такой, что API отвечает 400.
        YouTubeLocaleFix.apply(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
