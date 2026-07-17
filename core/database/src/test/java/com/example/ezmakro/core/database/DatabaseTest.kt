package com.example.ezmakro.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Base class for Room DAO tests using an in-memory database that is wiped
 * between tests (ADR-002: Room is the source of truth; these tests validate
 * that contract directly). Robolectric provides the Android Context and SQLite
 * implementation for JVM unit tests.
 */
@RunWith(RobolectricTestRunner::class)
abstract class DatabaseTest {
    protected lateinit var db: EZDatabase

    @Before
    fun createDb() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EZDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDb() {
        db.close()
    }
}
