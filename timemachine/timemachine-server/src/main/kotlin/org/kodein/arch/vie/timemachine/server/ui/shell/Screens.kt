package org.kodein.arch.vie.timemachine.server.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kodein.arch.vie.timemachine.protocol.TimeMachineConnection
import org.kodein.arch.vie.timemachine.server.store.ListenerState
import org.kodein.arch.vie.timemachine.server.ui.common.HorizontalRule
import org.kodein.arch.vie.timemachine.server.ui.common.LiveDot
import org.kodein.arch.vie.timemachine.server.ui.common.TextAction
import org.kodein.arch.vie.timemachine.server.ui.common.action
import org.kodein.arch.vie.timemachine.server.ui.common.copyToClipboard
import org.kodein.arch.vie.timemachine.server.ui.core.InspectorPane
import org.kodein.arch.vie.timemachine.server.ui.core.LocalInspector
import org.kodein.arch.vie.timemachine.server.ui.theme.AppLockup
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorColors
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorPillShape
import org.kodein.arch.vie.timemachine.server.ui.theme.InspectorTheme
import org.kodein.arch.vie.timemachine.server.ui.theme.Symbol


/** No probe has connected yet: [EmptyContent] over where the listener stands, as the store says. */
@Composable
internal fun EmptyScreen() {
    InspectorPane(LocalInspector.current.statusBarPane()) { status -> EmptyContent(status) }
}

/** No probe has connected yet: the address to connect to, and how to add the probe to an app. */
@Composable
private fun EmptyContent(status: StatusBarVIE.Model) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            AppLockup()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Waiting for an app to connect", style = InspectorTheme.text.screenTitle, color = scheme.onSurface)
                AddressLine(status)
            }
            SnippetCard()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol("lock_open", size = 16.dp, tint = scheme.outline)
                Text(
                    "The connection is unencrypted and unauthenticated. The inspector listens on all network interfaces: anyone on the network path can read your app's state, and anything that reaches the port can pose as an app. Use it on trusted networks.",
                    style = InspectorTheme.text.body.copy(lineHeight = 16.sp), color = scheme.outline,
                )
            }
        }
    }
}

@Composable
private fun AddressLine(status: StatusBarVIE.Model) {
    val scheme = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val address = status.address
        if (address == null) {
            Text("Starting the listener…", style = body)
        } else {
            LiveDot(true)
            Text("Listening on", style = body)
            Text(address, style = InspectorTheme.text.monoTitle, color = scheme.onSurface, modifier = Modifier.background(scheme.surfaceContainerHigh, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
            CopyGlyph(address, CopyAddressDescription)
            Text(
                buildAnnotatedString {
                    append("· protocol ")
                    withStyle(SpanStyle(fontFamily = InspectorTheme.fonts.mono)) { append(status.protocolVersion.toString()) }
                },
                style = body,
            )
        }
    }
}

/** What the copy glyphs of the screens say they copy. */
internal const val CopyAddressDescription = "Copy the address"
internal const val CopyCommandDescription = "Copy the command"

/** A copy glyph that turns into a check once it has copied [text]; [description] is what it says it copies. */
@Composable
private fun CopyGlyph(text: String, description: String) {
    var copied by remember { mutableStateOf(false) }
    Symbol(
        if (copied) "check" else "content_copy", size = 16.dp,
        tint = if (copied) InspectorTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.action(description = description) { copyToClipboard(text); copied = true },
    )
}

private const val ProbeSnippet = """TimeMachine.start(TimeMachineConfig(appName = "MyApp", enabled = true))

class TaskListVM : VIEViewModel<…>(
    middlewares = listOf(TimeMachine.middleware()),
)"""

private val SnippetKeyword = Regex("\\b(class|true)\\b")

/** [ProbeSnippet] with its strings and its keywords coloured. */
private fun probeSnippetText(colors: InspectorColors): AnnotatedString = buildAnnotatedString {
    val lines = ProbeSnippet.lines()
    lines.forEachIndexed { index, line ->
        var rest = line
        while (rest.isNotEmpty()) {
            val quoted = rest.indexOf('"').takeIf { it >= 0 }
            val keyword = SnippetKeyword.find(rest)
            when {
                quoted != null && (keyword == null || quoted < keyword.range.first) -> {
                    val end = rest.indexOf('"', quoted + 1).let { if (it < 0) rest.length - 1 else it }
                    append(rest.substring(0, quoted))
                    withStyle(SpanStyle(color = colors.jsonString)) { append(rest.substring(quoted, end + 1)) }
                    rest = rest.substring(end + 1)
                }
                keyword != null -> {
                    append(rest.substring(0, keyword.range.first))
                    withStyle(SpanStyle(color = colors.jsonLiteral)) { append(keyword.value) }
                    rest = rest.substring(keyword.range.last + 1)
                }
                else -> { append(rest); rest = "" }
            }
        }
        if (index < lines.lastIndex) append("\n")
    }
}

@Composable
private fun SnippetCard() {
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainer, shape).border(1.dp, scheme.outlineVariant, shape)) {
        Row(Modifier.fillMaxWidth().height(34.dp).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add the probe to your app", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextAction("Copy", glyph = "content_copy") { copyToClipboard(ProbeSnippet) }
        }
        HorizontalRule()
        Text(
            remember(colors) { probeSnippetText(colors) },
            style = InspectorTheme.text.monoTitle.copy(lineHeight = 20.sp), color = scheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/**
 * The listener did not start: nothing can connect until it does. A port in use is the one [reason] the
 * screen can tell what to do about; for any other, it shows the failure's own [message].
 */
@Composable
internal fun CannotListenScreen(reason: ListenerState.Failed.Reason, message: String, onRetry: () -> Unit) {
    val inUse = reason == ListenerState.Failed.Reason.PortInUse
    val scheme = MaterialTheme.colorScheme
    val colors = InspectorTheme.colors
    val port = TimeMachineConnection.port
    val body = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface)
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(48.dp).background(colors.errorContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Symbol("portable_wifi_off", size = 26.dp, tint = colors.error)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildAnnotatedString {
                        append("Can't listen on port ")
                        withStyle(SpanStyle(fontFamily = InspectorTheme.fonts.mono)) { append(port.toString()) }
                    },
                    style = InspectorTheme.text.screenHeadline, color = scheme.onSurface,
                )
                Text(
                    if (inUse) "Another program is already using it — usually a second TimeMachine inspector. No app can connect until the port is free."
                    else "The listener could not start. No app can connect until it does.",
                    style = InspectorTheme.text.emptyTitle.copy(lineHeight = 20.sp), color = scheme.onSurfaceVariant,
                )
            }
            if (inUse) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Step(1) { Text("Close the other inspector window.", style = body) }
                Step(2) {
                    Text("Not sure what holds it? Run", style = body)
                    Text("lsof -i :$port", style = InspectorTheme.text.code, color = scheme.onSurface, modifier = Modifier.background(scheme.surfaceContainerHigh, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    CopyGlyph("lsof -i :$port", CopyCommandDescription)
                }
                Step(3) { Text("Retry.", style = body) }
            } else Text(
                message, style = InspectorTheme.text.code, color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Row(
                Modifier.height(34.dp).background(scheme.primary, InspectorPillShape)
                    .action(onClick = onRetry).padding(start = 14.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("refresh", size = 18.dp, tint = scheme.onPrimary)
                Text("Retry", style = InspectorTheme.text.actionLarge, color = scheme.onPrimary)
            }
            Text(if (inUse) "$message · 0.0.0.0:$port" else "0.0.0.0:$port", style = InspectorTheme.text.codeRow, color = scheme.outline)
        }
    }
}

@Composable
private fun Step(number: Int, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number.toString(), style = InspectorTheme.text.monoNumeral, color = MaterialTheme.colorScheme.outline)
        content()
    }
}
