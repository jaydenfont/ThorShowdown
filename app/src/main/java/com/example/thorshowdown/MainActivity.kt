package com.example.thorshowdown

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.thorshowdown.ui.BottomScreen
import com.example.thorshowdown.ui.GameViewModel
import com.example.thorshowdown.ui.TopScreen
import com.example.thorshowdown.ui.theme.ThorShowdownTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels { GameViewModel.Factory }
    private var bottomPresentation: Presentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ThorShowdownTheme {
                val state by viewModel.topState.collectAsState()
                TopScreen(state)
            }
        }

        showBottomScreen()
    }

    override fun onDestroy() {
        bottomPresentation?.dismiss()
        super.onDestroy()
    }

    private fun showBottomScreen() {
        val display =
            getSystemService(DisplayManager::class.java)
                .displays
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY } ?: return

        // The bottom screen never opens dialog windows: one opened from this (the Activity's) context
        // would land on the top display, and the Presentation's context refuses them. It uses
        // InlineDialog instead.
        val composeView =
            ComposeView(this).apply {
                setContent {
                    ThorShowdownTheme {
                        val state by viewModel.bottomState.collectAsState()
                        BottomScreen(state, viewModel::onAction)
                    }
                }
            }

        bottomPresentation =
            Presentation(this, display).apply {
                setContentView(composeView)
                // Compose needs these owners, which a Presentation window doesn't provide.
                window?.decorView?.let {
                    it.setViewTreeLifecycleOwner(this@MainActivity)
                    it.setViewTreeViewModelStoreOwner(this@MainActivity)
                    it.setViewTreeSavedStateRegistryOwner(this@MainActivity)
                }
                show()
            }
    }
}
