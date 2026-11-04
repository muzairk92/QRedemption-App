package com.bingoinnovation.redemptiontester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bingoinnovation.redemptiontester.ui.navigation.AppNavHost
import com.bingoinnovation.redemptiontester.ui.theme.RedemptionTesterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory((application as RedemptionTesterApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splashScreen.setKeepOnScreenCondition { viewModel.startRoute.value == null }

        val container = (application as RedemptionTesterApplication).container
        setContent {
            RedemptionTesterTheme {
                val startRoute by viewModel.startRoute.collectAsStateWithLifecycle()
                startRoute?.let { AppNavHost(container = container, startRoute = it) }
            }
        }
    }
}
