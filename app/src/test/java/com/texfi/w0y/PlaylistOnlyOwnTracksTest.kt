package com.texfi.w0y

import com.texfi.w0y.data.ReorderPlan
import com.texfi.w0y.data.YtJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Плейлист аккаунта — только свои треки, рекомендации под ним отдельно. */
class PlaylistOnlyOwnTracksTest {
    private fun row(id: String) =
        """{"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"$id"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"t$id"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"a"}]}}}
          ]}}"""

    private fun page(ownContinues: Boolean = true) =
        """
        {"contents":{"sectionListRenderer":{"contents":[
          {"musicPlaylistShelfRenderer":{"contents":[${row("own00000001")},${row("own00000002")}]
            ${if (ownContinues) ",\"continuations\":[{\"nextContinuationData\":{\"continuation\":\"OWN_TOKEN\"}}]" else ""}}},
          {"musicShelfRenderer":{"contents":[${row("sug00000001")}],
            "continuations":[{"nextContinuationData":{"continuation":"SUGGEST_TOKEN"}}]}}
        ]}}}
        """.trimIndent()

    @Test
    fun suggestionsAreNotPartOfThePlaylist() {
        val root = Json.parseToJsonElement(page())
        assertEquals(listOf("own00000001", "own00000002"), YtJson.playlistTracks(root, first = true).map { it.id })
        // Без фильтра в список попала бы и рекомендация.
        assertEquals(3, YtJson.songs(root).size)
    }

    @Test
    fun continuationIsThePlaylistOne() {
        val root = Json.parseToJsonElement(page())
        assertEquals("OWN_TOKEN", YtJson.playlistContinuation(root))
    }

    @Test
    fun noContinuationWhenPlaylistEnds() {
        assertNull(YtJson.playlistContinuation(Json.parseToJsonElement(page(ownContinues = false))))
    }

    @Test
    fun reorderPlanReachesDesiredOrder() {
        val current = listOf("a", "b", "c", "d", "e")
        val desired = listOf("d", "a", "e", "b", "c")
        val order = current.toMutableList()
        ReorderPlan.moves(current, desired).forEach { (item, before) ->
            order.remove(item)
            order.add(order.indexOf(before), item)
        }
        assertEquals(desired, order)
    }

    @Test
    fun reorderPlanIsEmptyWhenNothingChanged() {
        assertEquals(emptyList<Pair<String, String>>(), ReorderPlan.moves(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun singleDragIsOneMove() {
        val moves = ReorderPlan.moves(listOf("a", "b", "c", "d"), listOf("a", "c", "b", "d"))
        assertEquals(1, moves.size)
    }
}
