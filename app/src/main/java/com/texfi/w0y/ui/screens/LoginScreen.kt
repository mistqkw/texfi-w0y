package com.texfi.w0y.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelTitle

/**
 * Вход в аккаунт YouTube Music.
 *
 * Своей формы входа здесь нет и не будет: пароль пользователь вводит только
 * на странице Google, приложение её не читает. После успешного входа берём
 * cookie, которые движок сохранил для music.youtube.com, — другого способа
 * авторизоваться у сторонних клиентов YouTube нет.
 *
 * Google иногда отвечает «этот браузер может быть небезопасен»: встроенный
 * движок он узнаёт по метке `wv` в User-Agent и по другим признакам. Метку
 * убираем, но полагаться на это нельзя — проверку могут вернуть в любой
 * момент. Поэтому рядом всегда доступен ручной путь: войти в обычном
 * браузере и вставить cookie сюда.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onCookie: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalW0yColors.current
    var manualMode by remember { mutableStateOf(false) }
    var manualCookie by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpriteButton(Sprites.previous, onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Text("вход", style = PixelTitle, color = colors.text, modifier = Modifier.weight(1f))
            PixelButton(
                text = if (manualMode) "ФОРМА" else "COOKIE",
                onClick = { manualMode = !manualMode },
                fill = colors.surfaceHigh,
            )
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        if (busy) {
            Text(
                text = "Проверяю вход…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }

        if (manualMode) {
            ManualCookie(
                value = manualCookie,
                busy = busy,
                onValueChange = { manualCookie = it },
                onSubmit = { onCookie(manualCookie.trim()) },
            )
        } else {
            GoogleForm(onCookie = onCookie)
        }
    }
}

@Composable
private fun GoogleForm(onCookie: (String) -> Unit) {
    val colors = LocalW0yColors.current
    // Движок создаётся один раз, а лямбда приходит новая на каждую
    // рекомпозицию — держим ссылку живой, иначе сработает устаревшая.
    val callback by rememberUpdatedState(onCookie)
    val submitted = remember { booleanArrayOf(false) }

    Text(
        text = "Это страница Google. Пароль вводится только на ней — приложение видит лишь результат входа.\n" +
            "Если Google скажет «этот браузер небезопасен» — нажми COOKIE сверху, там путь в обход.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
    )
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Метка «; wv» в User-Agent — то, по чему Google узнаёт
                // встроенный движок и отказывает во входе. Подменять агент
                // на десктопный хуже: несовпадение с платформой — отдельный
                // повод для отказа. Берём системный и убираем только метку.
                settings.userAgentString =
                    WebSettings
                        .getDefaultUserAgent(context)
                        .replace("; wv", "")
                        .replace(" wv", "")
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val cookie =
                                CookieManager.getInstance().getCookie("https://music.youtube.com")
                            // Ждём именно SAPISID: он появляется только после
                            // успешного входа, остальные cookie выставляются
                            // и анонимному посетителю. Отдаём один раз —
                            // onPageFinished срабатывает на каждый редирект.
                            if (cookie != null && "SAPISID" in cookie && !submitted[0]) {
                                submitted[0] = true
                                callback(cookie)
                            }
                        }
                    }
                loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/")
            }
        },
    )
}

@Composable
private fun ManualCookie(
    value: String,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalW0yColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(18.dp),
    ) {
        PixelCard(label = "COOKIE ВРУЧНУЮ", modifier = Modifier.fillMaxWidth()) {
            Text(
                text =
                    "Если Google отказывает окну приложения, войди в music.youtube.com " +
                        "в обычном браузере на компьютере и скопируй заголовок Cookie:\n\n" +
                        "1. Открой music.youtube.com уже под своим аккаунтом\n" +
                        "2. F12 → вкладка Network → обнови страницу\n" +
                        "3. Нажми на любой запрос к music.youtube.com\n" +
                        "4. В Request Headers найди строку Cookie и скопируй её целиком\n" +
                        "5. Вставь сюда\n\n" +
                        "Эта строка — ключ от аккаунта. Она хранится только на телефоне " +
                        "и уходит исключительно на серверы YouTube.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
        Spacer(Modifier.height(14.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(colors.surface)
                    .padding(12.dp),
        )
        Spacer(Modifier.height(14.dp))
        PixelButton(
            text = "ВОЙТИ",
            onClick = onSubmit,
            enabled = !busy && value.contains("SAPISID"),
        )
        if (value.isNotBlank() && !value.contains("SAPISID")) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "В строке нет SAPISID — похоже, скопирована не та часть или вход не выполнен.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
            )
        }
    }
}
