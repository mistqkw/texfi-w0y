package com.texfi.w0y

/**
 * Версия для людей: «0.0.1 beta-1». В versionName стоит дефис («0.0.1-beta-1»),
 * потому что так читается тег релиза и сборочные файлы; на экране его заменяет
 * пробел. Суффикс debug- и bench-сборок («-debug») остаётся как есть.
 */
val appVersionLabel: String
    get() = BuildConfig.VERSION_NAME.replaceFirst("-beta", " beta")
