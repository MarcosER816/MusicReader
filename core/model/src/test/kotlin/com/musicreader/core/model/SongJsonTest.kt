package com.musicreader.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SongJsonTest {
    @Test
    fun fullSongSurvivesRoundTrip() {
        val text = SongJson.encode(TestSongs.fullSong)
        assertEquals(TestSongs.fullSong, SongJson.decode(text))
    }

    @Test
    fun encodedSongHasSchemaVersionAndReadableEnums() {
        val text = SongJson.encode(TestSongs.fullSong.copy(schemaVersion = 0))
        val obj = SongJson.json.parseToJsonElement(text).jsonObject
        assertEquals(1, obj["schemaVersion"]!!.jsonPrimitive.int)
        assertEquals("es", obj["language"]!!.jsonPrimitive.content)
        assertTrue("\"PRE_CHORUS\"" !in text && "\"CHORUS\"" in text)
        assertTrue("\"HAMMER_ON\"" in text)
    }

    @Test
    fun recordingAndSettingsSurviveRoundTrip() {
        val json = SongJson.json
        val recording = TestSongs.fullRecording
        assertEquals(recording, json.decodeFromString(Recording.serializer(), json.encodeToString(Recording.serializer(), recording)))

        val settings = AppSettings(
            leftHanded = true,
            latencyProfiles = mapOf(
                AudioRoute.WIRED to LatencyProfile(21.0, LatencyMethod.AUTOMATIC, 0.95, 1_700_000_000_000),
                AudioRoute.BLUETOOTH to LatencyProfile(180.0, LatencyMethod.MANUAL, 0.6, 1_700_000_000_000),
            ),
            uiLanguage = UiLanguage.ES,
        )
        assertEquals(settings, json.decodeFromString(AppSettings.serializer(), json.encodeToString(AppSettings.serializer(), settings)))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val obj = SongJson.json.parseToJsonElement(SongJson.encode(TestSongs.fullSong)).jsonObject
        val withExtra = JsonObject(obj + ("futureField" to JsonPrimitive("x")))
        assertEquals(TestSongs.fullSong, SongJson.decode(withExtra.toString()))
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val minimal = """{"id":"s","title":"T","createdAt":1,"updatedAt":2}"""
        val song = SongJson.decode(minimal)
        assertEquals(Song(id = "s", title = "T", createdAt = 1, updatedAt = 2), song)
        assertEquals(120.0, song.tempoBpm)
        assertEquals(TimeSignature.FOUR_FOUR, song.timeSignature)
        assertEquals(Instrument.STANDARD_TUNING, song.instrument.tuning)
    }

    @Test
    fun newerSchemaVersionIsRejected() {
        val future = """{"id":"s","schemaVersion":2,"title":"T","createdAt":1,"updatedAt":2}"""
        val e = assertFailsWith<UnsupportedSchemaVersionException> { SongJson.decode(future) }
        assertEquals(2, e.version)
    }

    @Test
    fun migrationHookUpgradesOldVersionsStepByStep() {
        // A pretend schema 3 where v1 -> v2 renamed "name" to "title" and v2 -> v3 added an artist.
        val migrations = SchemaMigrations(
            currentVersion = 3,
            steps = mapOf(
                1 to { o -> JsonObject(o - "name" + ("title" to o.getValue("name"))) },
                2 to { o -> JsonObject(o + ("artist" to JsonPrimitive("Unknown"))) },
            ),
        )
        val v1 = """{"id":"s","schemaVersion":1,"name":"Old","createdAt":1,"updatedAt":2}"""
        val migrated = migrations.migrate(SongJson.json.parseToJsonElement(v1).jsonObject)
        assertEquals(3, migrated["schemaVersion"]!!.jsonPrimitive.int)
        assertEquals("Old", migrated["title"]!!.jsonPrimitive.content)
        assertEquals("Unknown", migrated["artist"]!!.jsonPrimitive.content)
    }

    @Test
    fun migrationsMustCoverEveryOldVersion() {
        assertFailsWith<IllegalArgumentException> { SchemaMigrations(currentVersion = 2) }
    }

    @Test
    fun invalidTimeSignatureInFileIsRejected() {
        val bad = """{"id":"s","title":"T","timeSignature":{"num":4,"den":3},"createdAt":1,"updatedAt":2}"""
        assertFailsWith<IllegalArgumentException> { SongJson.decode(bad) }
    }
}
