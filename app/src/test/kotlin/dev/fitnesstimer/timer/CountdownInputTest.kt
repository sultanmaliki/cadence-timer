package dev.fitnesstimer.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownInputTest {
    @Test fun minutesAndSeconds() {
        assertEquals(25 * 60_000L, parseCountdown("25:00"))
        assertEquals(90_000L, parseCountdown("1:30"))
        assertEquals(90_000L, parseCountdown("01:30"))
        assertEquals(5_000L, parseCountdown("0:05"))
        assertEquals(65_000L, parseCountdown("1:5"))        // single-digit seconds are fine
    }

    @Test fun hoursMinutesSeconds() {
        assertEquals(3_600_000L, parseCountdown("1:00:00"))
        assertEquals(3_661_000L, parseCountdown("1:01:01"))
        assertEquals(MAX_COUNTDOWN_MS, parseCountdown("99:59:59"))
    }

    @Test fun surroundingWhitespaceIsIgnored() = assertEquals(60_000L, parseCountdown("  1:00 "))

    @Test fun zeroIsParsedAndLeftToTheCaller() = assertEquals(0L, parseCountdown("0:00"))

    @Test fun rejectsGarbage() {
        for (bad in listOf("", ":", "5", "abc", "1:", ":30", "1::30", "1:2:3:4", "1.5:00", "1:30.5", "1 :30", "١:٣٠"))
            assertNull("'$bad'", parseCountdown(bad))
    }

    @Test fun rejectsSignsAndNegatives() {
        for (bad in listOf("-1:00", "+1:00", "1:-30", "--:--"))
            assertNull("'$bad'", parseCountdown(bad))
    }

    @Test fun secondsAndInnerMinutesMustBeBelow60() {
        assertNull(parseCountdown("1:60")); assertNull(parseCountdown("1:99"))
        assertNull(parseCountdown("1:60:00")); assertNull(parseCountdown("1:00:60"))
    }

    @Test fun hugeValuesAreRejectedNotOverflowed() {
        // 9999999999999999 minutes used to overflow Long arithmetic into a garbage target.
        assertNull(parseCountdown("9999999999999999:00"))
        assertNull(parseCountdown("999999:59"))        // > 99:59:59
        assertNull(parseCountdown("6000:00"))          // 100 hours
        assertEquals(5_999L * 60_000 + 59_000, parseCountdown("5999:59"))   // exactly the maximum
    }
}
