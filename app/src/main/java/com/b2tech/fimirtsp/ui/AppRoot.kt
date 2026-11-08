package com.b2tech.fimirtsp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.b2tech.fimirtsp.R

/**
 * Casca com as duas abas: Transmitir (publicador, tela original) e Assistir
 * (reprodutor). Duas destinações fixas não justificam navigation-compose.
 * Trocar de aba tira a outra de composição — é isso que libera o player
 * (DisposableEffect no PlayerScreen).
 */
@Composable
fun AppRoot(
    transmitir: @Composable () -> Unit,
    assistir: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Cast, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_transmit)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.LiveTv, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_watch)) },
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (tab == 0) transmitir() else assistir()
        }
    }
}
