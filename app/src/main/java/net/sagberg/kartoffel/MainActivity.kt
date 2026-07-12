package net.sagberg.kartoffel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import net.sagberg.kartoffel.map.CoverageMapScreen
import net.sagberg.kartoffel.ui.theme.KartoffelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0, 0),
            navigationBarStyle = SystemBarStyle.auto(0, 0),
        )
        setContent {
            KartoffelApp()
        }
    }
}

@Composable
private fun KartoffelApp() {
    KartoffelTheme {
        CoverageMapScreen()
    }
}
