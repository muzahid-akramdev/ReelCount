package dev.akram.reelcount

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dev.akram.reelcount.ui.ReelCountNav
import dev.akram.reelcount.ui.theme.ReelCountTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReelCountTheme {
                ReelCountNav()
            }
        }
    }
}
