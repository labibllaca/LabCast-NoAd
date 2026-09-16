package com.example

import com.example.data.TranscriptParser
import com.example.util.PodcastDateUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testDateParsing() {
    val date1 = "Thu, 10 Sep 2026 08:00:00 -0000"
    val ts1 = PodcastDateUtils.parseDateToTimestamp(date1)
    assertTrue("Timestamp should be > 0 for $date1, was $ts1", ts1 > 0L)

    val date2 = "Mon, 07 Sep 2026 00:29:00 -0000"
    val ts2 = PodcastDateUtils.parseDateToTimestamp(date2)
    assertTrue("Timestamp should be > 0 for $date2, was $ts2", ts2 > 0L)

    val date3 = "2026-09-04"
    val ts3 = PodcastDateUtils.parseDateToTimestamp(date3)
    assertTrue("Timestamp should be > 0 for $date3, was $ts3", ts3 > 0L)

    assertTrue("Date1 should be after Date2", ts1 > ts2)
  }

  @Test
  fun testTranscriptParsing() {
    val rawWithBrackets = """
      [00:00] Host: Welcome everyone
      [01:30] Host: Sponsor break with AG1
      [03:45.500] Speaker 2: Let's talk about neuroscience
    """.trimIndent()

    val segments = TranscriptParser.parseOrGenerateTranscript(
      rawTranscript = rawWithBrackets,
      episodeTitle = "Test Episode",
      episodeDescription = "Test Description",
      durationSeconds = 600L
    )

    println("Parsed segments: " + segments.map { "${it.startTimeSeconds}s: ${it.speaker} - ${it.text}" })
    assertEquals(3, segments.size)
    assertEquals(0L, segments[0].startTimeSeconds)
    assertEquals(90L, segments[1].startTimeSeconds)
    assertEquals(225L, segments[2].startTimeSeconds)
  }

  @Test
  fun testIncrementalVersionCheck() {
    // Test newer patch versions
    assertTrue(com.example.util.GitHubUpdateManager.isNewerVersion("v1.2.2", "v1.2.1"))
    assertTrue(com.example.util.GitHubUpdateManager.isNewerVersion("1.3.0", "v1.2.1"))
    assertTrue(com.example.util.GitHubUpdateManager.isNewerVersion("v2.0.0", "v1.2.1"))
    assertTrue(com.example.util.GitHubUpdateManager.isNewerVersion("v1.2.1.1", "v1.2.1"))

    // Test same or older versions
    assertFalse(com.example.util.GitHubUpdateManager.isNewerVersion("v1.2.1", "v1.2.1"))
    assertFalse(com.example.util.GitHubUpdateManager.isNewerVersion("1.2.1", "1.2.1"))
    assertFalse(com.example.util.GitHubUpdateManager.isNewerVersion("v1.2.0", "v1.2.1"))
    assertFalse(com.example.util.GitHubUpdateManager.isNewerVersion("v1.1.9", "v1.2.1"))
    assertFalse(com.example.util.GitHubUpdateManager.isNewerVersion("v1.0.0", "v1.2.1"))
  }

  @Test
  fun testSmartDownloadExpirationThreshold() {
    val twoDaysMillis = 2 * 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()

    // 1 day old download -> not expired
    val oneDayOld = now - (24 * 60 * 60 * 1000L)
    val isOneDayExpired = (now - oneDayOld) > twoDaysMillis
    assertFalse(isOneDayExpired)

    // 2 days and 1 hour old download -> expired
    val twoDaysOneHourOld = now - (49 * 60 * 60 * 1000L)
    val isTwoDaysExpired = (now - twoDaysOneHourOld) > twoDaysMillis
    assertTrue(isTwoDaysExpired)
  }
}
