package com.educash.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.educash.mobile.ui.login.LoginViewModel
import com.educash.mobile.ui.navigation.AppNavigation
import com.educash.mobile.ui.navigation.Screen
import com.educash.mobile.ui.theme.EduCashTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EduCashTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val loginViewModel: LoginViewModel = viewModel()
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
