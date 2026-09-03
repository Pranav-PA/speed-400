package dev.pranav.speed400garage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.pranav.speed400garage.ui.GarageApp
import dev.pranav.speed400garage.ui.theme.Speed400GarageTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Speed400GarageTheme { GarageApp() }
        }
    }
}
