package dev.akram.reelcount

import dev.akram.reelcount.domain.SyncBuckets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class SyncBucketsTest {
    @Test
    fun groupsByLocalHourAndApp() {
        val zone = ZoneId.of("Asia/Dhaka")
        val events =
            listOf(
                1_760_640_000_000L to "com.instagram.android",
                1_760_640_000_000L + 1_000L to "com.instagram.android",
                1_760_640_000_000L + 3_700_000L to "com.google.android.youtube",
            )
        val buckets = SyncBuckets.hourly(events, zone)
        assertEquals(2, buckets.size)
        assertEquals(2, buckets.first { it.app == "com.instagram.android" }.count)
        assertEquals(1, buckets.first { it.app == "com.google.android.youtube" }.count)
    }
}
