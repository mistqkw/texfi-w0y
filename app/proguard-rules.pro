# innertubex и его модели ходят через kotlinx.serialization: имена полей
# нужны в рантайме, иначе релизная сборка перестаёт разбирать ответы
# YouTube — и ломается ровно то, что на debug работает.
-keep,includedescriptorclasses class com.metrolist.innertubex.**$$serializer { *; }
-keepclassmembers class com.metrolist.innertubex.** {
    *** Companion;
}
-keepclasseswithmembers class com.metrolist.innertubex.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 через рефлексию поднимает рендереры и фабрики источников.
-dontwarn androidx.media3.**

# Ktor выбирает движок сервис-лоадером.
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# jump3r: высокоуровневый LameEncoder ссылается на javax.sound, которого в Android нет;
# мы его не используем, кодер собирается из низкоуровневых классов.
-dontwarn javax.sound.**
