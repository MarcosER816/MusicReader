package com.musicreader.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Thrown when a song file was written by a newer app version than this one. */
class UnsupportedSchemaVersionException(val version: Int, val supported: Int) :
    IllegalArgumentException("Song schemaVersion $version is newer than supported version $supported")

/**
 * Upgrades song JSON from older schema versions, one step at a time.
 *
 * [steps] maps a version `v` to a function that turns a version-`v` object into a
 * version-`v + 1` object. Every version below [currentVersion] needs a step. To change the
 * schema: bump [SongJson.CURRENT_SCHEMA_VERSION], add the step to [SongJson.MIGRATIONS] and add
 * a unit test that decodes a file of the old version.
 */
class SchemaMigrations(
    val currentVersion: Int,
    private val steps: Map<Int, (JsonObject) -> JsonObject> = emptyMap(),
) {
    init {
        for (v in 1 until currentVersion) {
            require(v in steps) { "Missing migration step from schema version $v to ${v + 1}" }
        }
    }

    /** Returns [song] upgraded to [currentVersion]. A missing `schemaVersion` counts as 1. */
    fun migrate(song: JsonObject): JsonObject {
        var version = song[SCHEMA_VERSION_KEY]?.jsonPrimitive?.int ?: 1
        if (version > currentVersion) throw UnsupportedSchemaVersionException(version, currentVersion)
        var current = song
        while (version < currentVersion) {
            val step = steps.getValue(version)
            version += 1
            current = JsonObject(step(current) + (SCHEMA_VERSION_KEY to JsonPrimitive(version)))
        }
        return current
    }

    companion object {
        const val SCHEMA_VERSION_KEY: String = "schemaVersion"
    }
}

/** Reads and writes song files. */
object SongJson {
    const val CURRENT_SCHEMA_VERSION: Int = 1

    /** Migration steps for older song files. Empty while the schema is at version 1. */
    val MIGRATIONS: SchemaMigrations = SchemaMigrations(currentVersion = CURRENT_SCHEMA_VERSION)

    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(song: Song): String =
        json.encodeToString(Song.serializer(), song.copy(schemaVersion = CURRENT_SCHEMA_VERSION))

    /** Parses a song file, upgrading older schema versions with [migrations]. */
    fun decode(text: String, migrations: SchemaMigrations = MIGRATIONS): Song {
        val migrated = migrations.migrate(json.parseToJsonElement(text).jsonObject)
        return json.decodeFromJsonElement(Song.serializer(), migrated)
    }
}
