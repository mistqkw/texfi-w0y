package com.texfi.w0y.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.R
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelTitle

@Composable
fun HomeScreen() {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.home_title),
            style = PixelTitle,
            color = colors.text,
        )
        Spacer(Modifier.height(18.dp))
        PixelCard(label = "СЕЙЧАС", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.home_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
        Spacer(Modifier.height(14.dp))
        PixelCard(label = "ДАЛЬШЕ", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.home_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.skeleton_note, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}
