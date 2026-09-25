package com.texfi.w0y.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.texfi.w0y.ui.components.SpriteButton
import com.texfi.w0y.ui.components.Sprites
import com.texfi.w0y.ui.theme.LocalW0yColors
import com.texfi.w0y.ui.theme.PixelTitle

/**
 * Вход в аккаунт — настоящая форма Google в окне приложения.
 *
 * Своей формы входа здесь нет и не будет: пароль пользователь вводит
 * только на странице Google, приложение её не читает. После успешного
 * входа забираем cookie, которые движок сохранил для music.youtube.com, —
 * другого способа авторизоваться у сторонних клиентов YouTube нет.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onCookie: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalW0yColors.current

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
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
            Text("вход", style = PixelTitle, color = colors.text)
        }
        Text(
            text = "Это страница Google. Пароль вводится только на ней — приложение видит лишь результат входа.",
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
                    // Десктопный User-Agent: со стандартным WebView-агентом
                    // Google часто отвечает «этот браузер небезопасен».
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val cookie =
                                    CookieManager.getInstance().getCookie("https://music.youtube.com")
                                // Ждём именно SAPISID: он появляется только
                                // после успешного входа, остальные cookie
                                // выставляются и анонимному посетителю.
                                if (cookie != null && "SAPISID" in cookie) {
                                    onCookie(cookie)
                                }
                            }
                        }
                    loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/")
                }
            },
        )
    }
}
