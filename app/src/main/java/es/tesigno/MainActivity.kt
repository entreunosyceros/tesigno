package es.tesigno

import android.os.Bundle
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private var splashDeadline = 0L

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("splashDeadline", splashDeadline)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // La espera no bloquea el hilo principal ni vuelve a empezar al rotar.
        splashDeadline = savedInstanceState?.getLong("splashDeadline")
            ?: (SystemClock.elapsedRealtime() + 3_000L)
        splash.setKeepOnScreenCondition { SystemClock.elapsedRealtime() < splashDeadline }
        splash.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge()
        setContent { es.tesigno.ui.TeSignoApp() }
    }
}
