package org.kodein.arch.vie.timemachine.server.store

import kotlin.time.Instant


/** How many buckets the activity sparkline has. */
const val ActivityBuckets: Int = 10

/** How long one bucket of the activity sparkline is. */
const val ActivityBucketMillis: Long = 3_000

/**
 * How many rows a ViewModel had in each of its newest [ActivityBuckets] buckets of
 * [ActivityBucketMillis], counted by the store so that eviction does not thin them.
 *
 * Buckets are aligned to absolute time, so a count only ever lands in the bucket its row's display
 * time belongs to: between two boundaries, only the last bucket grows.
 *
 * @property newest The index of the newest bucket, `epochMillis.floorDiv(ActivityBucketMillis)`.
 * @property counts The counts of the buckets ending at [newest], oldest first.
 */
data class Activity(val newest: Long, val counts: List<Int>) {

    /**
     * This activity once a row displayed at [at] is counted. A row older than the oldest bucket is
     * not: it could never show.
     */
    fun counting(at: Instant): Activity {
        val bucket = bucketOf(at)
        return when {
            bucket > newest -> {
                val shift = minOf(bucket - newest, ActivityBuckets.toLong()).toInt()
                val shifted = counts.drop(shift) + List(shift) { 0 }
                Activity(bucket, shifted.with(ActivityBuckets - 1, +1))
            }
            bucket <= newest - ActivityBuckets -> this
            else -> copy(counts = counts.with(ActivityBuckets - 1 - (newest - bucket).toInt(), +1))
        }
    }

    /** The count of bucket number [bucket], `0` outside the ten this holds. */
    private fun countAt(bucket: Long): Int {
        val back = newest - bucket
        return if (back in 0 until ActivityBuckets) counts[ActivityBuckets - 1 - back.toInt()] else 0
    }

    internal fun sparkline(now: Instant): List<Int> {
        val last = bucketOf(now)
        // A row ahead of now (a probe's clock running fast) counts in the last bucket.
        val ahead = (last + 1..newest).sumOf { countAt(it) }
        return List(ActivityBuckets) { countAt(last - (ActivityBuckets - 1 - it)) + if (it == ActivityBuckets - 1) ahead else 0 }
    }

    companion object {
        internal fun bucketOf(at: Instant): Long = at.toEpochMilliseconds().floorDiv(ActivityBucketMillis)

        internal fun first(at: Instant): Activity = Activity(bucketOf(at), List(ActivityBuckets) { if (it == ActivityBuckets - 1) 1 else 0 })
    }
}

private fun List<Int>.with(index: Int, delta: Int): List<Int> = mapIndexed { i, count -> if (i == index) count + delta else count }

/** The ten bucket counts ending at the bucket of [now], oldest first; all zeros when nothing was counted. */
internal fun Activity?.sparkline(now: Instant): List<Int> = this?.sparkline(now) ?: List(ActivityBuckets) { 0 }

/** This activity once a row displayed at [at] is counted. */
internal fun Activity?.counting(at: Instant): Activity = this?.counting(at) ?: Activity.first(at)
