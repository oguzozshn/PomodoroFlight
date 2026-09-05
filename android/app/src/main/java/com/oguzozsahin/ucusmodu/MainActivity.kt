package com.oguzozsahin.ucusmodu

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oguzozsahin.ucusmodu.data.WindowStore
import com.oguzozsahin.ucusmodu.timer.FlightEngine
import com.oguzozsahin.ucusmodu.timer.Notifications
import com.oguzozsahin.ucusmodu.ui.FlightActions
import com.oguzozsahin.ucusmodu.ui.FlightRoot
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.UcusModuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engine = FlightEngine.get(this)
        val actions = FlightActions(
            onToggle = engine::toggle,
            onSkip = engine::skip,
            onReset = engine::reset,
            onRoute = engine::setRoute,
            onFocusMin = engine::setFocusMin,
            onBreakMin = engine::setBreakMin,
            onAutoBreak = engine::setAutoBreak,
            onSound = engine::setSound,
            onResetEverything = engine::resetEverything,
            onStart = engine::start,
            onPassenger = engine::setPassenger,
            onTicketPrinted = engine::vibrateTicket,
            onTearTick = engine::vibrateTick,
        )

        val windowStore = WindowStore(this)

        setContent {
            // Tema tercihi burada tutuluyor: degisince butun agac yeniden renklenir.
            var theme by rememberSaveable { mutableStateOf(windowStore.loadTheme()) }

            UcusModuTheme(choice = theme) {
                val state by engine.state.collectAsStateWithLifecycle()

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* reddedilse de sayac calisir, yalnizca bildirim gorunmez */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !Notifications.canPost(this@MainActivity)
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Surface(color = LocalFlightColors.current.ground) {
                    FlightRoot(
                        state = state,
                        actions = actions,
                        theme = theme,
                        onTheme = {
                            theme = it
                            windowStore.saveTheme(it)
                        },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Uygulama arka plandayken gecen sureyi yakala.
        FlightEngine.get(this).syncNow()
    }
}
