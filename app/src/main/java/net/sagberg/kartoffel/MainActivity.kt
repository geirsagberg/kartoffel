package net.sagberg.kartoffel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.sagberg.kartoffel.map.CoverageMapScreen

private const val SYSTEM_BAR_COLOR = 0xFFFFFBFE.toInt()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                SYSTEM_BAR_COLOR,
                SYSTEM_BAR_COLOR,
            ),
            navigationBarStyle = SystemBarStyle.light(
                SYSTEM_BAR_COLOR,
                SYSTEM_BAR_COLOR,
            ),
        )
        setContent {
            KartoffelApp()
        }
    }
}

@Composable
private fun KartoffelApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                CoverageMapScreen()
                SystemBarBackgrounds()
            }
        }
    }
}

@Composable
private fun BoxScope.SystemBarBackgrounds() {
    val color = MaterialTheme.colorScheme.surface

    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .background(color),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.navigationBars)
            .background(color),
    )
}
