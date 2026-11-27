package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.example.di.HookAppContainer
import com.example.security.configureAppCheck
import com.example.ui.navigation.HookNavGraph
import com.example.ui.theme.HookedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val container = remember { HookAppContainer(applicationContext) }
            HookedTheme {
                HookNavGraph(container = container)
            }
        }
    }
}
