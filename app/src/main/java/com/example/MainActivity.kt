package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TerminalScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TerminalViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val terminalViewModel: TerminalViewModel = viewModel()
                    TerminalScreen(viewModel = terminalViewModel)
                }
            }
        }
    }
}
