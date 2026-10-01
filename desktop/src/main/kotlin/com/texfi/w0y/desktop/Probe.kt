package com.texfi.w0y.desktop

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val yt = Yt()
    val found = yt.search("kai angel paris 2008")
    println("songs=${found.songs.size} albums=${found.albums.size} artists=${found.artists.size}")
    val song = found.songs.first()
    println("first: ${song.artist} - ${song.title} (${song.id})")
    val s = yt.stream(song.id)
    println("stream: itag=${s.itag} mime=${s.mimeType} chunks=${s.useRangeChunks} headers=${s.headers.keys}")
    var last = 0.0
    var dur = 0.0
    val mpv = Mpv({ last = it }, { dur = it }, {}, { println("end: $it") }, { println("file-loaded") })
    println("mpv started: ${mpv.start()}")
    mpv.load(s.audioUrl, s.headers)
    Thread.sleep(8000)
    println("time-pos=$last duration=$dur")
    mpv.shutdown()
}
