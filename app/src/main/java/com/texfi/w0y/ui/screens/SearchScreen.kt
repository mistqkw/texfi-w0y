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
import com.texfi.w0y.R
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelTitle

@Composable
fun SearchScreen() {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.tab_search),
            style = PixelTitle,
            color = colors.text,
        )
        Spacer(Modifier.height(18.dp))
        // Честно: поля ввода-пустышки здесь нет. Пока поиск не ходит в
        // YouTube Music, экран говорит это прямым текстом.
        PixelCard(label = "ПОИСК", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.search_not_wired),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
    }
}
