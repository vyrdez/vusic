package com.feishin.mobile.core.common

/**
 * Segment maths for the Storage > Usage bar, kept pure so the proportions are
 * unit-testable.
 *
 * The bar used to divide each category by *Vusic's own usage only* and leave
 * free space out entirely, so the largest category was always ~100% of the bar
 * (347 MB of song cache next to 3.94 GB free filled it end to end) and the bar
 * could never show how much room was left. Now every segment - including free
 * space - is a share of one total, so widths are actually proportional.
 */
object UsageBarMath {

    /** A non-zero segment never gets thinner than this, or it vanishes. */
    const val MIN_SEGMENT_FRACTION = 0.02f

    /** Below this share of the total, a fraction rounds to nothing. */
    const val EPSILON = 0.0001f

    data class Segment(
        val label: String,
        val bytes: Long,
        val colorIndex: Int,
    )

    data class Result(
        /** Fractions that sum to 1 (after the minimum-width correction). */
        val fractions: List<Float>,
        /** Same order, but every entry is at least [MIN_SEGMENT_FRACTION]. */
        val bytes: List<Long>,
        val totalBytes: Long,
        val usedBytes: Long,
        val freeBytes: Long,
    )

    /**
     * @param vusicSegments Vusic's own storage, in bar order.
     * @param freeBytes device free space, shown as the last (neutral) segment.
     */
    fun compute(
        vusicSegments: List<Segment>,
        freeBytes: Long,
    ): Result {
        val bytes = vusicSegments.map { it.bytes.coerceAtLeast(0L) } + listOf(freeBytes.coerceAtLeast(0L))
        val used = vusicSegments.sumOf { it.bytes.coerceAtLeast(0L) }
        val total = used + freeBytes.coerceAtLeast(0L)

        // Nothing to show yet: an all-zero bar would divide by zero and flicker.
        if (total <= 0L) {
            return Result(
                fractions = List(bytes.size) { 0f },
                bytes = bytes,
                totalBytes = 0L,
                usedBytes = 0L,
                freeBytes = 0L,
            )
        }

        val raw = bytes.map { it.toFloat() / total.toFloat() }
        val largest = raw.indices.maxByOrNull { raw[it] } ?: 0
        // Visibility is decided by the BYTES, not the fraction: a real 1-byte
        // entry next to a terabyte of free space is a fraction of 1e-12, but it
        // still deserves its slice of the bar. A zero-byte entry stays at zero.
        val floored = bytes.mapIndexed { i, b ->
            if (b > 0L) maxOf(raw[i], MIN_SEGMENT_FRACTION) else 0f
        }
        val delta = 1f - floored.sum()
        val corrected = floored.toMutableList().also {
            it[largest] = (it[largest] + delta).coerceIn(0f, 1f)
        }
        return Result(
            fractions = corrected.map { it.coerceIn(0f, 1f) },
            bytes = bytes,
            totalBytes = total,
            usedBytes = used,
            freeBytes = freeBytes.coerceAtLeast(0L),
        )
    }

    /** "1.2 GB used by Vusic, 3.9 GB free". */
    fun totalLabel(usedBytes: Long, freeBytes: Long, human: (Long) -> String): String =
        "${human(usedBytes)} used by Vusic, ${human(freeBytes.coerceAtLeast(0L))} free"
}