package com.bingoinnovation.redemptiontester

import android.app.Application
import com.bingoinnovation.redemptiontester.di.AppContainer

class RedemptionTesterApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
