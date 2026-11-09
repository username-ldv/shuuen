package ldv.shuuen.core.database

import androidx.room3.migration.Migration

/**
 * Every schema change bumps [AppDatabase]'s version and adds a migration: an `AutoMigration` on
 * the `@Database` annotation when Room can derive it from the exported schemas in
 * `shared/schemas`, otherwise a [Migration] here. Room checks the migrated schema when the
 * database opens, so a wrong migration fails at once instead of corrupting data.
 */
internal val Migrations: Array<Migration> = arrayOf()

/**
 * Schema versions 1–7 only existed in development builds before the first release. Nothing
 * migrates them; a database still on one of them is rebuilt empty.
 */
internal val UnmigratedVersions: IntArray = (1..7).toList().toIntArray()
