package com.example.msdksample

import android.app.Application
import android.content.Context
import android.util.Log


class MyApplication : Application() {
    @Override
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        com.secneo.sdk.Helper.install(this)
    }
}