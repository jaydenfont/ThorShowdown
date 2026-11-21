package com.example.thorshowdown

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Hello World" })
        val dm = getSystemService(DisplayManager::class.java)
        val bottom = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY } ?: return
        Presentation(this, bottom).apply {
            setContentView(TextView(context).apply { text = "Hello World" })
            show()
        }
    }
}
