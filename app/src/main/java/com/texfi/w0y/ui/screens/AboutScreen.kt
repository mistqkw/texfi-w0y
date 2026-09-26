package com.texfi.w0y.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.texfi.w0y.BuildConfig
import com.texfi.w0y.R
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.PixelSprite
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.components.pressScale
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelBigNumber
import com.texfi.w0y.ui.theme.PixelSectionLabel
import com.texfi.w0y.ui.theme.PixelTitle

/**
 * «О приложении» — тот же экран, что в m0ney, и намеренно тот же.
 *
 * Смысл не в версии и не в лицензии: это единственное место, где человек,
 * поставивший w0y из каталога, узнаёт, что у автора есть ещё три
 * приложения. Сайт он не увидит никогда — цепочку замыкает вот этот
 * список, и поэтому он здесь не рекламный блок, а часть продукта.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalW0yColors.current
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        // Открывать нечем — не падаем: на голой системе без браузера
        // это нормальная ситуация, а не ошибка приложения.
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpriteButton(Sprites.chevronLeft, onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.about_title), style = PixelTitle, color = colors.text)
        }

        LazyColumn(
            Modifier.navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { NumberedSection("01", stringResource(R.string.about_section_app)) }
            item { AppFacts() }

            item { NumberedSection("02", stringResource(R.string.about_section_open)) }
            item {
                LinkRow(
                    sprite = Sprites.github,
                    title = stringResource(R.string.about_source_title),
                    subtitle = REPO_SHORT,
                    onClick = { open(REPO_URL) },
                )
            }
            item {
                LinkRow(
                    sprite = Sprites.license,
                    title = stringResource(R.string.about_license_title),
                    subtitle = stringResource(R.string.about_license_text),
                    onClick = { open(LICENSE_URL) },
                )
            }
            item {
                LinkRow(
                    sprite = Sprites.globe,
                    title = stringResource(R.string.about_ecosystem_title),
                    subtitle = HUB_SHORT,
                    onClick = { open(HUB_URL) },
                )
            }

            item { NumberedSection("03", stringResource(R.string.about_section_family)) }
            item {
                LinkRow(
                    sprite = Sprites.markFokus,
                    title = "TexFi f0kus",
                    subtitle = stringResource(R.string.about_fokus_text),
                    onClick = { open("$HUB_URL/download/fokus") },
                )
            }
            item {
                LinkRow(
                    sprite = Sprites.markMoney,
                    title = "TexFi m0ney",
                    subtitle = stringResource(R.string.about_money_text),
                    onClick = { open("$HUB_URL/download/money") },
                )
            }
            item {
                LinkRow(
                    sprite = Sprites.markFiles,
                    title = "TexFi files",
                    subtitle = stringResource(R.string.about_files_text),
                    onClick = { open("$HUB_URL/download/files") },
                )
            }

            item { NumberedSection("04", stringResource(R.string.about_section_support)) }
            item { DonateCard(onClick = { open(DONATE_URL) }) }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

/** Заголовок раздела: номер акцентом, дальше подпись и линия до края. */
@Composable
private fun NumberedSection(number: String, title: String) {
    val colors = LocalW0yColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number, style = PixelSectionLabel, color = colors.accent)
        Spacer(Modifier.width(10.dp))
        Text(title, style = PixelTitle, color = colors.text)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(2.dp)
                .background(colors.border),
        )
    }
}

/** Карточка с версией и тем, что про приложение можно сказать честно. */
@Composable
private fun AppFacts() {
    val colors = LocalW0yColors.current
    PixelCard {
        Text(
            text = "texfi w0y",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = PixelBigNumber,
            color = colors.text,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.about_build, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.text,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.about_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(12.dp))
        // Цифры не переводятся, поэтому в ресурсах лежат только подписи.
        Fact("0", stringResource(R.string.about_fact_ads))
        Fact("0", stringResource(R.string.about_fact_telemetry))
        Fact("AGPL-3.0", stringResource(R.string.about_fact_license))
    }
}

/** Строка факта: квадратная точка, значение пиксельным, подпись обычным. */
@Composable
private fun Fact(value: String, label: String) {
    val colors = LocalW0yColors.current
    Row(
        Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(colors.accent))
        Spacer(Modifier.width(8.dp))
        Text(value, style = PixelSectionLabel, color = colors.text)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = PixelSectionLabel,
            color = colors.textMuted,
        )
    }
}

/** Карточка-ссылка: знак слева, стрелка «наружу» справа. */
@Composable
private fun LinkRow(
    sprite: List<String>,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressed = 0.985f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelSprite(rows = sprite, color = colors.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.text,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.width(10.dp))
            PixelSprite(
                rows = Sprites.chevronRight,
                color = colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Единственная кнопка в приложении, которая просит денег. Без нажима. */
@Composable
private fun DonateCard(onClick: () -> Unit) {
    val colors = LocalW0yColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressed = 0.985f)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.about_section_support),
                style = PixelSectionLabel,
                color = colors.accent,
            )
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelSprite(
                    rows = Sprites.heart,
                    color = colors.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.about_donate_title),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.text,
                )
            }
            Text(
                text = stringResource(R.string.about_donate_text),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Start,
            )
        }
    }
}

private const val REPO_URL = "https://github.com/mistqkw/texfi-w0y"
private const val REPO_SHORT = "github.com/mistqkw/texfi-w0y"
private const val LICENSE_URL = "https://github.com/mistqkw/texfi-w0y/blob/main/LICENSE"
private const val HUB_URL = "https://texfi-hub.vercel.app"
private const val HUB_SHORT = "texfi-hub.vercel.app"

/** Тот же адрес, что у остальных приложений TexFi: копилка одна на всех. */
private const val DONATE_URL = "https://github.com/sponsors/mistqkw"
