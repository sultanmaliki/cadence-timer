package dev.fitnesstimer.timer

/** Longest countdown accepted: 99:59:59 — what the timer text can show. */
const val MAX_COUNTDOWN_MS = ((99L * 60 + 59) * 60 + 59) * 1000

/**
 * Pure. Parses what the user types in the countdown dialog into milliseconds, or null if it isn't a
 * valid duration. Accepts `mm:ss` ("25:00", "1:30") and `h:mm:ss` ("1:00:00"). Digits only (no
 * signs, spaces inside, or decimals); seconds must be 0..59, and in `h:mm:ss` minutes too. A huge
 * number of minutes used to overflow into a garbage (possibly negative or tiny) target, so the
 * result is capped by rejecting anything above [MAX_COUNTDOWN_MS]. A zero duration is valid here
 * (the caller decides whether to allow it).
 */
fun parseCountdown(input: String): Long? {
    val parts = input.trim().split(":")
    if (parts.size !in 2..3) return null
    if (parts.any { it.isEmpty() || it.length > 6 || !it.all { c -> c in '0'..'9' } }) return null
    val nums = parts.map { it.toLong() }
    val hours = if (parts.size == 3) nums[0] else 0L
    val minutes = if (parts.size == 3) nums[1] else nums[0]
    val seconds = nums.last()
    if (seconds > 59) return null
    if (parts.size == 3 && minutes > 59) return null
    val totalSeconds = (hours * 60 + minutes) * 60 + seconds
    val ms = totalSeconds * 1000
    return if (ms in 0..MAX_COUNTDOWN_MS) ms else null
}
