package com.texfi.w0y.ui.screens

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.texfi.w0y.R
import com.texfi.w0y.ui.components.PixelButton
import com.texfi.w0y.ui.components.PixelCard
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.screenBackground
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
 *
 * Аккаунт берётся из системы: стандартный выбор аккаунтов Android показывает
 * те, что уже есть на телефоне, и отдаёт только адрес — без разрешений и без
 * доступа к самому аккаунту. Адрес подставляется в форму Google, так что
 * остаётся одно действие: пароль или подтверждение на телефоне. Войти «само»
 * по системному аккаунту стороннее приложение не может: токены, из которых
 * получается сессия YouTube, Google выдаёт только своим приложениям.
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
    // null — выбор ещё не закрыт; "" — выбрать отказались, форма пустая.
    var account by rememberSaveable { mutableStateOf<String?>(null) }
    val chooser =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            account =
                result.data
                    ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    ?.takeIf { result.resultCode == Activity.RESULT_OK }
                    .orEmpty()
        }
    val pickAccount = {
        runCatching {
            chooser.launch(
                AccountManager.newChooseAccountIntent(
                    null, null, arrayOf(GOOGLE_ACCOUNT_TYPE), null, null, null, null,
                ),
            )
        }.onFailure { account = "" }
        Unit
    }
    LaunchedEffect(Unit) { if (account == null) pickAccount() }

    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpriteButton(Sprites.chevronLeft, onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.login_title), style = PixelTitle, color = colors.text, modifier = Modifier.weight(1f))
            PixelButton(
                text = if (manualMode) stringResource(R.string.login_tab_form) else "COOKIE",
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
                text = stringResource(R.string.login_checking),
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
            when (val email = account) {
                null ->
                    Text(
                        text = stringResource(R.string.login_picking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(18.dp),
                    )
                else ->
                    key(email) {
                        GoogleForm(email = email, onPickAccount = pickAccount, onCookie = onCookie)
                    }
            }
        }
    }
}

@Composable
private fun GoogleForm(
    email: String,
    onPickAccount: () -> Unit,
    onCookie: (String) -> Unit,
) {
    val colors = LocalW0yColors.current
    // Движок создаётся один раз, а лямбда приходит новая на каждую
    // рекомпозицию — держим ссылку живой, иначе сработает устаревшая.
    val callback by rememberUpdatedState(onCookie)
    val submitted = remember { booleanArrayOf(false) }
    // Как только Google отпустил на YouTube, страницу музыки уже никто не
    // ждёт: движок прячется, сессия забирается из cookie сразу.
    var finishing by remember { mutableStateOf(false) }

    Text(
        text =
            if (email.isNotEmpty()) {
                stringResource(R.string.login_account_hint, email)
            } else {
                stringResource(R.string.login_google_note) + stringResource(R.string.login_google_blocked)
            },
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
    )
    PixelButton(
        text = stringResource(R.string.login_other_account),
        onClick = onPickAccount,
        fill = colors.surfaceHigh,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
    )
    Box(Modifier.fillMaxSize()) {
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
                            // Проверяем на старте каждой страницы, а не на
                            // конце: раньше вход ждал полной загрузки
                            // music.youtube.com, и человек видел сайт музыки
                            // перед тем, как попасть в приложение.
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                val host = url?.let(Uri::parse)?.host.orEmpty()
                                if (host.endsWith("youtube.com") && !host.startsWith("accounts.")) {
                                    finishing = true
                                    if (trySubmit()) view?.stopLoading()
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                // Страница догрузилась, а сессии так и нет —
                                // значит, вход не прошёл; прятать её дальше
                                // значило бы держать человека перед надписью.
                                if (!trySubmit()) finishing = false
                            }

                            // Ждём именно SAPISID: он появляется только после
                            // успешного входа, остальные cookie выставляются
                            // и анонимному посетителю. Отдаём один раз —
                            // страницы в цепочке редиректов идут одна за другой.
                            private fun trySubmit(): Boolean {
                                if (submitted[0]) return true
                                val cookie =
                                    CookieManager.getInstance().getCookie("https://music.youtube.com")
                                if (cookie == null || "SAPISID" !in cookie) return false
                                submitted[0] = true
                                callback(cookie)
                                return true
                            }
                        }
                    loadUrl(loginUrl(email))
                }
            },
        )
        if (finishing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.background)
                    .padding(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_finishing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.accent,
                )
            }
        }
    }
}

/** Адрес подставляется самой форме Google — никуда, кроме неё, он не уходит. */
private fun loginUrl(email: String): String =
    Uri
        .parse("https://accounts.google.com/ServiceLogin")
        .buildUpon()
        .appendQueryParameter("service", "youtube")
        .appendQueryParameter("continue", "https://music.youtube.com/")
        .apply { if (email.isNotEmpty()) appendQueryParameter("Email", email) }
        .build()
        .toString()

private const val GOOGLE_ACCOUNT_TYPE = "com.google"

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
        PixelCard(label = stringResource(R.string.login_tab_cookie), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.login_cookie_howto),
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
            text = stringResource(R.string.library_sign_in),
            onClick = onSubmit,
            enabled = !busy && value.contains("SAPISID"),
        )
        if (value.isNotBlank() && !value.contains("SAPISID")) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.login_no_sapisid),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
            )
        }
    }
}
