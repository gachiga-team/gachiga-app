package com.gachiga.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gachiga.app.ui.home.HomeScreen
import com.gachiga.app.ui.home.HomeViewModel
import com.gachiga.app.ui.theme.GachigaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GachigaApp).container
        setContent {
            GachigaTheme {
                val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                HomeScreen(vm)
            }
        }
    }
}
