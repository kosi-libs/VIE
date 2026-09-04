package org.kodein.mvivm.tm.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.singleWindowApplication
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

const val TIME_MACHINE_PORT = 13229

fun startTimeMachineServer(): EmbeddedServer<*, *> =
    embeddedServer(CIO, port = TIME_MACHINE_PORT) {
        install(WebSockets)
        routing {
            webSocket("/timemachine") {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        println("TimeMachine <- ${frame.readText()}")
                    }
                }
            }
        }
    }.start(wait = false)

fun main() {
    val server = startTimeMachineServer()
    println("TimeMachine websocket server listening on port $TIME_MACHINE_PORT")
    try {
        singleWindowApplication(title = "MVI-VM TimeMachine") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Hello World!")
            }
        }
    } finally {
        server.stop()
    }
}
