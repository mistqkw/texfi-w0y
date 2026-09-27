package com.texfi.w0y

import org.junit.Assume.assumeTrue

/**
 * Живые тесты ходят в настоящий YouTube.
 *
 * Это осмысленно на машине разработчика: разбор ответов ломается не от
 * наших правок, а от изменений на их стороне, и поймать это можно только
 * настоящим запросом. Но с раннеров GitHub Actions YouTube отвечает
 * отказом — IP дата-центра, — и такие падения говорят не о коде, а о том,
 * откуда пришёл запрос. Красный CI, который ничего не значит, хуже, чем
 * его отсутствие: на него перестают смотреть.
 *
 * Поэтому в CI живые тесты пропускаются, а локально идут как шли. Чтобы
 * запустить их в CI намеренно: `W0Y_LIVE_TESTS=1`.
 */
fun requireLiveNetwork() {
    val forced = System.getenv("W0Y_LIVE_TESTS") != null
    val ci = System.getenv("CI") != null
    assumeTrue("Живые запросы к YouTube пропущены в CI", forced || !ci)
}

/**
 * Подпись ссылки на поток YouTube расшифровывает JS-движком QuickJS, а тот —
 * нативная библиотека, собранная только под ABI Android. Когда YouTube
 * отдаёт формат с подписью, извлечение на десктопной JVM заканчивается
 * UnsatisfiedLinkError: библиотеки в java.library.path просто нет. На
 * телефоне тот же путь работает.
 *
 * Такое падение говорит про машину, а не про код, — поэтому оно
 * превращается в пропуск. Когда YouTube отдаёт ссылку без подписи, тест
 * идёт до конца и проверяет ровно то, для чего написан.
 */
fun skipIfNativeCipherMissing(error: Throwable) {
    var cause: Throwable? = error
    var depth = 0
    while (cause != null && depth++ < 32) {
        if (cause is UnsatisfiedLinkError) {
            assumeTrue(
                "QuickJS собран только под Android: расшифровку подписи на JVM не проверить",
                false,
            )
        }
        cause = cause.cause
    }
}
