package com.texfi.w0y

import com.texfi.w0y.data.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergeTest {
    private fun s(vararg ids: String) = ids.toSet()

    @Test
    fun freshImportTakesEverythingFromAccount() {
        val plan = SyncMerge.plan(base = s(), local = s(), remote = s("a", "b"), remoteComplete = true)
        assertEquals(s("a", "b"), plan.pullAdd)
        assertEquals(s(), plan.pushAdd)
        assertEquals(s(), plan.pullRemove)
    }

    @Test
    fun localAdditionGoesUpAndAccountAdditionComesDown() {
        val plan = SyncMerge.plan(base = s("a"), local = s("a", "l"), remote = s("a", "r"), remoteComplete = true)
        assertEquals(s("l"), plan.pushAdd)
        assertEquals(s("r"), plan.pullAdd)
    }

    @Test
    fun removalOnEitherSideIsCarriedOver() {
        val plan = SyncMerge.plan(base = s("a", "b", "c"), local = s("a", "c"), remote = s("a", "b"), remoteComplete = true)
        assertEquals(s("b"), plan.pushRemove)
        assertEquals(s("c"), plan.pullRemove)
        assertEquals(s(), plan.pullAdd)
    }

    @Test
    fun removedOnBothSidesDoesNothing() {
        val plan = SyncMerge.plan(base = s("a"), local = s(), remote = s(), remoteComplete = true)
        assertEquals(s(), plan.pushRemove)
        assertEquals(s(), plan.pullRemove)
    }

    @Test
    fun incompleteAccountListNeverRemovesNorPushes() {
        val plan = SyncMerge.plan(base = s("a", "b"), local = s("a", "b", "n"), remote = s("a"), remoteComplete = false)
        assertEquals(s(), plan.pullRemove)
        assertEquals(s(), plan.pushAdd)
    }

    @Test
    fun emptyAnswerInsteadOfFullListIsNotADeletion() {
        val plan = SyncMerge.plan(base = s("a", "b"), local = s("a", "b"), remote = s(), remoteComplete = true)
        assertEquals(s(), plan.pullRemove)
    }

    @Test
    fun firstSyncOfOldMirroredPlaylistOnlyPushesMissingTracks() {
        val plan = SyncMerge.plan(base = s(), local = s("a", "b"), remote = s("b", "c"), remoteComplete = true)
        assertEquals(s("a"), plan.pushAdd)
        assertEquals(s("c"), plan.pullAdd)
        assertEquals(s(), plan.pullRemove)
    }
}
