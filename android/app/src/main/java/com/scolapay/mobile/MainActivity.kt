package com.scolapay.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.scolapay.mobile.ui.login.LoginViewModel
import com.scolapay.mobile.ui.navigation.AppNavigation
import com.scolapay.mobile.ui.navigation.Screen
import com.scolapay.mobile.ui.theme.ScolaPayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScolaPayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var startDestination by remember { mutableStateOf<String?>(null) }
                    val loginViewModel: LoginViewModel = hiltViewModel()

                    LaunchedEffect(Unit) {
                        startDestination = if (loginViewModel.checkAuth()) {
                            Screen.Dashboard.route
                        } else {
                            Screen.Login.route
                        }
                    }

                    startDestination?.let { destination ->
                        AppNavigation(startDestination = destination)
                    }
                }
            }
        }
    }
}
