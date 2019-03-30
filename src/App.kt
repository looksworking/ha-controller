package org.looksworking.ha.controller

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.net.InetSocketAddress

fun Application.main() {

    embeddedServer(Netty, port = 18080) {
        routing {
            get("/") {
                call.respondText("Hello World from what is supposed to be WWW server!", ContentType.Text.Plain)
            }
        }
    }.start()

    embeddedServer(Netty, port = 18090) {
        routing {
            get("/") {
                call.respondText("Hello World from what is supposed to be RESt server!", ContentType.Text.Plain)
            }
        }
    }.start()

    runBlocking {
        org.looksworking.ha.controller.Application.logger.info("Starting UDP")
        val server = aSocket(ActorSelectorManager(Dispatchers.IO))
            .udp()
            .bind(InetSocketAddress("0.0.0.0", 18070))
        while (true) {
            org.looksworking.ha.controller.Application.logger.info(server.incoming.receive().packet.readText())
        }
    }
}


class Application {
    companion object : KLogging()
}
