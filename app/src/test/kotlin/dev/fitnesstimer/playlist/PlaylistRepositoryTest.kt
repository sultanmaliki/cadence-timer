package dev.fitnesstimer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaylistRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun repo(name: String = "p.json") = PlaylistRepository(File(tmp.root, name))

    @Test fun missingFileLoadsEmpty() = assertTrue(repo().load().isEmpty())

    @Test fun roundTrip() {
        val data = listOf(
            Playlist("1", "Legs \"day\" ☃", listOf("content://a/1", "content://b/2 with space")),
            Playlist("2", "Empty", emptyList()),
        )
        val r = repo(); r.save(data)
        assertEquals(data, r.load())
    }

    @Test fun overwriteReplacesPrevious() {
        val r = repo()
        r.save(listOf(Playlist("1", "a", listOf("x"))))
        r.save(listOf(Playlist("2", "b", emptyList())))
        assertEquals(listOf(Playlist("2", "b", emptyList())), r.load())
    }

    @Test fun corruptFileLoadsEmptyInsteadOfCrashing() {
        val f = File(tmp.root, "p.json"); f.writeText("{not json")
        assertTrue(PlaylistRepository(f).load().isEmpty())
    }

    @Test fun saveLeavesNoTempFileBehind() {
        repo().save(listOf(Playlist("1", "a", emptyList())))
        assertEquals(listOf("p.json"), tmp.root.list()!!.toList())
    }
}
