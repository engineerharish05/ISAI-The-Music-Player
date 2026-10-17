package com.rishinnovations.isai.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineParsersTest {
    @Test fun archiveSearchParsesDocs() {
        val json = """{"response":{"docs":[
            {"identifier":"abc","title":"Night Songs","creator":["A","B"],"licenseurl":"https://creativecommons.org/licenses/by-sa/4.0/"},
            {"title":"no id"},
            {"identifier":"xyz"}
        ]}}"""
        val items = OnlineParsers.archiveSearch(json)
        assertEquals(2, items.size)
        assertEquals("A, B", items[0].creator)
        assertEquals("xyz", items[1].title)
    }

    @Test fun archiveMetadataPrefersMp3AndBuildsHttpsUrl() {
        val item = OnlineParsers.ArchiveItem("abc", "Album", "Artist", "https://creativecommons.org/licenses/by/3.0/")
        val json = """{"files":[
            {"name":"cover.jpg","format":"JPEG"},
            {"name":"01 One.mp3","format":"VBR MP3","title":"One","length":"185.5"},
            {"name":"01 One.ogg","format":"Ogg Vorbis","length":"185.5"}
        ]}"""
        val tracks = OnlineParsers.archiveMetadata(json, item)
        assertEquals(1, tracks.size)
        val t = tracks.single()
        assertEquals("One", t.title)
        assertEquals(185_500L, t.durationMs)
        assertEquals("https://archive.org/download/abc/01%20One.mp3", t.streamUrl)
        assertEquals("CC BY 3.0", t.license)
        assertEquals("mp3", t.fileExtension)
        assertEquals("online:archive:abc/01 One.mp3", t.mediaId)
    }

    @Test fun archiveMetadataFallsBackToOgg() {
        val item = OnlineParsers.ArchiveItem("abc", "Album", "Artist", null)
        val tracks = OnlineParsers.archiveMetadata("""{"files":[{"name":"a.ogg","format":"Ogg Vorbis"}]}""", item)
        assertEquals(1, tracks.size)
        assertEquals("See item page", tracks.single().license)
    }

    @Test fun jamendoParsesAndRejectsInsecureOrMissingAudio() {
        val json = """{"results":[
            {"id":"1","name":"Song","artist_name":"Band","album_name":"LP","duration":200,"audio":"https://prod.jamendo/a.mp3","image":"https://img/x.jpg","license_ccurl":"http://creativecommons.org/licenses/by-nc-nd/3.0/"},
            {"id":"2","name":"Insecure","audio":"http://x/a.mp3"},
            {"name":"NoId","audio":"https://x/a.mp3"}
        ]}"""
        val tracks = OnlineParsers.jamendo(json)
        assertEquals(1, tracks.size)
        assertEquals(200_000L, tracks[0].durationMs)
        assertEquals("CC BY-NC-ND 3.0", tracks[0].license)
    }

    @Test fun lengthParsing() {
        assertEquals(0L, OnlineParsers.parseLength(""))
        assertEquals(245_000L, OnlineParsers.parseLength("4:05"))
        assertEquals(3_725_000L, OnlineParsers.parseLength("1:02:05"))
        assertEquals(12_500L, OnlineParsers.parseLength("12.5"))
    }

    @Test fun licenseNames() {
        assertEquals("Public domain (ZERO)", OnlineParsers.licenseName("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertTrue(OnlineParsers.licenseName("https://example.com/x").startsWith("https://"))
    }

    @Test fun badJsonIsHandledByCaller() {
        val result = runCatching { OnlineParsers.jamendo("not json") }
        assertTrue(result.isFailure)
    }
}
