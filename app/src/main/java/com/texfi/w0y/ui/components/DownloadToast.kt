package com.texfi.w0y.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.texfi.w0y.R
import com.texfi.w0y.playback.DownloadNotice
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelSectionLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Строка над мини-плеером: что стало с нажатием «скачать» и где искать.
 *
 * Появляется на каждое нажатие и на итог пачки, живёт несколько секунд,
 * новая заменяет старую. «Открыть» ведёт прямо в загрузки — ответ на
 * «а где это», не выходя из того, что слушаешь.
 */
@Composable
fun DownloadToast(
    notices: Flow<DownloadNotice>,
    onOpen: () -> Unit,
) {
    val colors = LocalW0yColors.current
    var current by remember { mutableStateOf<DownloadNotice?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(notices) {
        notices.collectLatest { notice ->
            current = notice
            visible = true
            delay(SHOW_MS)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(160)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(140)) + fadeOut(tween(120)),
    ) {
        val notice = current ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .background(colors.surfaceHigh)
                .border(2.dp, colors.border)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = noticeText(notice),
                style = MaterialTheme.typography.bodySmall,
                color = if (notice is DownloadNotice.Failed || notice is DownloadNotice.StorageFull) colors.secondary else colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.download_open),
                style = PixelSectionLabel,
                color = colors.accent,
                modifier =
                    Modifier
                        .clickable {
                            visible = false
                            onOpen()
                        }.padding(6.dp),
            )
        }
    }
}

@Composable
private fun noticeText(notice: DownloadNotice): String =
    when (notice) {
        is DownloadNotice.Queued ->
            when {
                notice.waitingForWifi && notice.count == 1 -> stringResource(R.string.download_wifi_one, notice.title)
                notice.waitingForWifi -> stringResource(R.string.download_wifi_many, notice.count)
                notice.count == 1 -> stringResource(R.string.download_queued_one, notice.title)
                else -> stringResource(R.string.download_queued_many, notice.count)
            }
        is DownloadNotice.Finished ->
            notice.title?.let { stringResource(R.string.download_done_one, it) }
                ?: stringResource(R.string.download_done_many, notice.count)
        is DownloadNotice.Failed ->
            stringResource(R.string.dl_failed_with, notice.title, stringResource(notice.reason.label))
        is DownloadNotice.StorageFull -> stringResource(R.string.dl_storage_full, notice.limitMb)
        DownloadNotice.PausedForBattery -> stringResource(R.string.dl_paused_battery)
    }

private const val SHOW_MS = 4_000L
