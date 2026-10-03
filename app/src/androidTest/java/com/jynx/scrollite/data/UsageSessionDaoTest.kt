package com.jynx.scrollite.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class UsageSessionDaoTest {

    private val zone = ZoneId.of("America/New_York")
    private val oct2 = DayBounds.of(LocalDate.of(2026, 10, 2), zone)
    private val oct3 = DayBounds.of(LocalDate.of(2026, 10, 3), zone)
    private val min = 60_000L

    private lateinit var db: AppDatabase
    private lateinit var dao: UsageSessionDao
    private val shortForm = Section.shortForm.map { it.name }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.usageSessionDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(startMs: Long, endMs: Long, section: Section = Section.SHORT_VIDEO) =
        dao.insert(UsageSession(startMs = startMs, endMs = endMs, platform = Platform.INSTAGRAM, section = section))

    @Test
    fun sumsSessionsInsideTheDay() = runBlocking {
        insert(oct2.startMs + 60 * min, oct2.startMs + 70 * min)
        insert(oct2.startMs + 120 * min, oct2.startMs + 125 * min)

        assertEquals(15 * min, dao.totalBetween(oct2.startMs, oct2.endMs, shortForm))
    }

    @Test
    fun sessionAcrossMidnight_isSplitBetweenDays() = runBlocking {
        // 23:50 Oct 2 → 00:10 Oct 3
        insert(oct2.endMs - 10 * min, oct2.endMs + 10 * min)

        assertEquals(10 * min, dao.totalBetween(oct2.startMs, oct2.endMs, shortForm))
        assertEquals(10 * min, dao.totalBetween(oct3.startMs, oct3.endMs, shortForm))
    }

    @Test
    fun onlyRequestedSectionsCount() = runBlocking {
        insert(oct2.startMs + 60 * min, oct2.startMs + 70 * min, Section.SHORT_VIDEO)
        insert(oct2.startMs + 70 * min, oct2.startMs + 100 * min, Section.OTHER)

        assertEquals(10 * min, dao.totalBetween(oct2.startMs, oct2.endMs, shortForm))
        assertEquals(40 * min, dao.totalBetween(oct2.startMs, oct2.endMs, Section.entries.map { it.name }))
    }

    @Test
    fun emptyDay_isZero() = runBlocking {
        insert(oct2.startMs + 60 * min, oct2.startMs + 70 * min)

        assertEquals(0L, dao.totalBetween(oct3.startMs, oct3.endMs, shortForm))
    }
}
