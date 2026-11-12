package org.kodein.arch.vie.timemachine.server.ui.common

import org.kodein.arch.vie.timemachine.server.store.ViewModelRecord
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.uuid.Uuid


/** A session's identifier as it is shown: its first and last four hexadecimal digits, `xxxx…xxxx`. */
fun shortSessionId(id: Uuid): String = id.toHexString().let { "${it.take(4)}…${it.takeLast(4)}" }

/** A ViewModel's identifier as it is shown: its first four hexadecimal digits. */
fun shortViewModelId(id: Uuid): String = id.toHexString().take(4)

/**
 * A ViewModel's name, as every pane shows it: the last segment of its [typeName], and its short
 * identifier while nothing describes it.
 */
fun viewModelName(typeName: String?, id: Uuid): String = typeName?.let { splitSerialName(it).last } ?: shortViewModelId(id)

/**
 * A ViewModel named in one line, where its name and its identifier have no slot of their own:
 * `TaskListVM · 3f2a`, and its short identifier alone while nothing describes it.
 */
fun viewModelLabel(typeName: String?, id: Uuid): String =
    typeName?.let { "${viewModelName(it, id)} · ${shortViewModelId(id)}" } ?: shortViewModelId(id)

/**
 * A serial name split for display: the muted [prefix], up to and including the last `.`, and the
 * emphasised [last] segment. A name with no `.` is all [last].
 */
data class SerialNameParts(val prefix: String, val last: String)

fun splitSerialName(name: String): SerialNameParts =
    name.lastIndexOf('.').let { SerialNameParts(name.substring(0, it + 1), name.substring(it + 1)) }

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/** [at] as `HH:mm:ss.SSS` in [zone]. */
fun formatTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    TimeFormat.withZone(zone).format(java.time.Instant.ofEpochSecond(at.epochSeconds, at.nanosecondsOfSecond.toLong()))

/** The number of intervention [requestId] within this ViewModel, from 1, in the order they were asked. */
fun ViewModelRecord.interventionNumber(requestId: Uuid): Int? =
    interventions.keys.indexOf(requestId).takeIf { it >= 0 }?.plus(1)
