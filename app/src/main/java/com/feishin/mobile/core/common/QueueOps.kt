package com.feishin.mobile.core.common

/**
 * Pure queue list operations, including index bookkeeping. Kept free of Android
 * and Media3 so the rules are unit-testable; [com.feishin.mobile.playback.PlayerManager]
 * mirrors these onto the MediaController.
 */
object QueueOps {

    /** Insert [item] directly after [currentIndex] (or at the head when currentIndex < 0). */
    fun <T> insertNext(queue: List<T>, item: T, currentIndex: Int): List<T> {
        val at = if (currentIndex < 0) 0 else (currentIndex + 1).coerceIn(0, queue.size)
        return queue.toMutableList().apply { add(at, item) }
    }

    fun <T> append(queue: List<T>, item: T): List<T> = queue + item

    fun <T> appendAll(queue: List<T>, items: List<T>): List<T> = queue + items

    /**
     * Removes the entry at [index]. Returns the new queue plus the adjusted
     * current index: removing the current item moves it to the same slot (or the
     * new last item), removing an earlier item shifts it back by one.
     */
    fun <T> removeAt(queue: List<T>, index: Int, currentIndex: Int): Pair<List<T>, Int> {
        if (index !in queue.indices) return queue to currentIndex
        val next = queue.toMutableList().apply { removeAt(index) }
        val adjusted = when {
            currentIndex == index -> if (next.isEmpty()) 0 else index.coerceAtMost(next.size - 1)
            currentIndex > index -> currentIndex - 1
            else -> currentIndex
        }
        return next to adjusted
    }

    /**
     * Removes exactly one entry, chosen by its stable id and never by a
     * position captured earlier. Entries keep their identity while the list
     * shifts under them, so the same song queued twice still loses only the
     * copy the user pointed at. An id that is already gone is a no-op, which
     * is what makes a repeated dismiss (a settle that replays, a rapid swipe
     * of a neighbour) harmless instead of a second removal.
     */
    fun <T> removeById(
        queue: List<T>,
        id: Long?,
        idOf: (T) -> Long,
        currentIndex: Int,
    ): Pair<List<T>, Int> {
        if (id == null) return queue to currentIndex
        val index = queue.indexOfFirst { idOf(it) == id }
        return removeAt(queue, index, currentIndex)
    }

    /** Moves an entry and keeps the current index pointing at the same item. */
    fun <T> move(queue: List<T>, from: Int, to: Int, currentIndex: Int): Pair<List<T>, Int> {
        if (from !in queue.indices || to !in queue.indices) return queue to currentIndex
        val current = queue.getOrNull(currentIndex)
        val next = queue.toMutableList().apply { add(to, removeAt(from)) }
        val adjusted = if (current != null) next.indexOf(current) else currentIndex.coerceIn(0, maxOf(0, next.size - 1))
        return next to adjusted
    }

    fun <T> clear(): Pair<List<T>, Int> = emptyList<T>() to 0

    /** Shuffled copy for the *actual* playback order; the visible order follows it. */
    fun <T> shuffled(queue: List<T>, random: kotlin.random.Random): List<T> = queue.shuffled(random)
}