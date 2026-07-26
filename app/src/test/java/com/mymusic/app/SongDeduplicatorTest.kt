package com.mymusic.app

import com.mymusic.app.data.model.ArtistMap
import com.mymusic.app.data.model.Song
import com.mymusic.app.data.model.SongArtists
import com.mymusic.app.utils.SongDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Test

class SongDeduplicatorTest {

    @Test
    fun testExactDuplicatesDeduplication() {
        val song1 = createSong("id1", "Kesariya", "Arijit Singh", duration = 268)
        val song2 = createSong("id2", "Kesariya", "Arijit Singh", duration = 268)

        val result = SongDeduplicator.deduplicate(listOf(song1, song2))
        assertEquals(1, result.size)
        assertEquals("id1", result.first().id)
    }

    @Test
    fun testRemixVsOriginalSelection() {
        val original = createSong("id_orig", "Kesariya", "Arijit Singh", duration = 268, playCount = 50000)
        val remix = createSong("id_remix", "Kesariya (Dance Remix)", "Arijit Singh", duration = 268, playCount = 10000)

        val result = SongDeduplicator.deduplicate(listOf(remix, original))
        assertEquals(1, result.size)
        assertEquals("id_orig", result.first().id)
    }

    @Test
    fun testFeaturedArtistMatch() {
        val song1 = createSong("id1", "Apna Bana Le", "Arijit Singh, Sachin-Jigar", duration = 261)
        val song2 = createSong("id2", "Apna Bana Le (from Bhediya)", "Arijit Singh", duration = 261)

        val result = SongDeduplicator.deduplicate(listOf(song1, song2))
        assertEquals(1, result.size)
    }

    @Test
    fun testDifferentSongsNotDeduplicated() {
        val song1 = createSong("id1", "Kesariya", "Arijit Singh", duration = 268)
        val song2 = createSong("id2", "Tum Hi Ho", "Arijit Singh", duration = 262)

        val result = SongDeduplicator.deduplicate(listOf(song1, song2))
        assertEquals(2, result.size)
    }

    private fun createSong(
        id: String,
        name: String,
        artistName: String,
        duration: Int,
        playCount: Int = 1000
    ): Song {
        return Song(
            id = id,
            name = name,
            duration = duration,
            playCount = playCount,
            artists = SongArtists(
                primary = listOf(ArtistMap(id = "a1", name = artistName, role = "primary"))
            )
        )
    }
}
