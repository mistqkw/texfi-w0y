import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// w0y для Linux: отдельный Gradle-проект рядом с Android-приложением.
// Общий с телефоном код — разбор ответов YouTube (YtJson, SongItem,
// SyncMerge) — подключается из app/ исходниками, а не копией: правка
// формата ответа в одном месте чинит оба приложения.
plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

kotlin { jvmToolchain(21) }

val shared = "../app/src/main/java/com/texfi/w0y/data"

sourceSets.main {
    kotlin.srcDir("src/main/kotlin")
}

// Только то, что не знает про Android.
val sharedSources = tasks.register<Sync>("sharedSources") {
    from(shared) {
        include("YtJson.kt", "SongItem.kt", "SyncMerge.kt", "CleanMatch.kt", "Profanity.kt")
    }
    into(layout.buildDirectory.dir("generated/shared/com/texfi/w0y/data"))
}
sourceSets.main { kotlin.srcDir(layout.buildDirectory.dir("generated/shared")) }
tasks.named("compileKotlin") { dependsOn(sharedSources) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.components.resources)
    implementation("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // D-Bus: управление с клавиатуры, из шторки и плагинов окружения (MPRIS).
    implementation("com.github.hypfvieh:dbus-java-core:5.1.0")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.0")
    implementation("org.slf4j:slf4j-nop:2.0.17")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
}

compose.desktop {
    application {
        mainClass = "com.texfi.w0y.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            // java.net.http нужен загрузкам и текстам; jlink сам его не находит.
            modules("java.net.http", "java.naming", "jdk.crypto.ec", "jdk.unsupported", "jdk.security.auth")
            packageName = "w0y"
            packageVersion = "0.0.2"
            description = "TexFi w0y — клиент YouTube Music"
            linux { iconFile.set(project.file("src/main/resources/icon.png")) }
        }
    }
}

// Проверка без окна: поиск, ссылка на звук и mpv. ./gradlew probe
tasks.register<JavaExec>("probe") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.texfi.w0y.desktop.ProbeKt"
}
