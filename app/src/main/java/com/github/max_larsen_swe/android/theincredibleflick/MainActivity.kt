package com.github.max_larsen_swe.android.theincredibleflick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import com.github.max_larsen_swe.android.theincredibleflick.backend.DownloadManager
import com.github.max_larsen_swe.android.theincredibleflick.backend.Keys
import com.github.max_larsen_swe.android.theincredibleflick.composables.UI
import com.github.max_larsen_swe.android.theincredibleflick.data.DC

class MainActivity : ComponentActivity() {
    companion object {
        var state = mutableStateListOf(DC())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Keys.initialize(BuildConfig.API_KEY, BuildConfig.API_SECRET)
        DownloadManager.initialize(cacheDir.path)
        onBackPressedDispatcher.addCallback(owner = this) { state.removeLastOrNull() }
        setContent { UI.App(state) }
    }

}

