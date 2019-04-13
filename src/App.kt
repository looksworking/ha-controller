package org.looksworking.ha.controller

import com.google.gson.Gson
import com.google.gson.JsonElement
import freemarker.cache.ClassTemplateLoader
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.freemarker.FreeMarker
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.http.ContentType
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.net.InetSocketAddress
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.time.format.DateTimeFormatter



fun Application.web() {
    routing {
        install(FreeMarker) {
            templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
        }
        get("/") {
            val connection = Database.connection
            val query = "select id, data from data where unit='74c7d675-9402-4ef4-afdb-55b2bf0b960e' " +
                    "ORDER BY id DESC LIMIT 1; "
            val resultSet = connection.createStatement().executeQuery(query)
            if(resultSet.next()) {
                val date = idToDate(resultSet.getLong("id"))
                val formatter = DateTimeFormatter.ofPattern("HH:mm:ss / dd.MM.yyyy")
                val formattedString = date.format(formatter)
                val lastData = resultSet.getString("data")
                val json = GsonSingleton.gson.fromJson(lastData, JsonElement::class.java).asJsonObject
                call.respond((FreeMarkerContent("index.ftl",
                    mapOf(
                        "time" to formattedString,
                        "temp" to json.getAsJsonPrimitive("temperature"),
                        "hum" to json.getAsJsonPrimitive("humidity")), "")))
            } else {
                call.respondText("No data", ContentType.Text.Plain)
            }
        }
    }
}

fun Application.rest() {
    routing {
        get("/") {
            call.respondText("Hello World from what is supposed to be rest server", ContentType.Text.Plain)
        }
    }
}

fun db(): Connection {
    val jarPath = Paths.get(object {}.javaClass.protectionDomain.codeSource.location.path).parent.parent
    val connection = DriverManager.getConnection("jdbc:sqlite:$jarPath/data.db")
    val statement = connection.createStatement()
    val createTable = "create table if not exists data (id integer, unit uuid, data json)"
    statement.executeUpdate(createTable)
    return connection
}

fun main() {

    val statement = Database.connection.createStatement()

    embeddedServer(Netty, port = 8080) {
        web()
    }.start()

    embeddedServer(Netty, port = 8090) {
        rest()
    }.start()

    runBlocking {
        val server = aSocket(ActorSelectorManager(Dispatchers.IO))
            .udp()
            .bind(InetSocketAddress("0.0.0.0", 18070))
        Main.logger.info("Started UDP")
        while (true) {
            val message = server.incoming.receive().packet.readText()
            Main.logger.info("Received message: [$message]")
            val id = generateId()

            val messageJson = GsonSingleton.gson.fromJson(message, JsonElement::class.java).asJsonObject
            messageJson.addProperty("id", id)
            val unitId = messageJson.getAsJsonPrimitive("unit").toString()

            val query = "insert into data values($id, $unitId, '$message')"
            Main.logger.info("Query: [$query]")
            statement.executeUpdate(query)
        }
    }
}

class Main {
    companion object : KLogging()
}

class Database {
    companion object {
        val connection = db()
    }
}

class GsonSingleton {
    companion object {
        val gson = Gson()
    }
}

fun generateId(): String {
    val start: Long = 1554350400000
    val now = Calendar.getInstance(TimeZone.getTimeZone("GMT")).timeInMillis
    val worker: Long = 0
    var id = ((now - start) shl (23))
    id = id or (worker shl (10))
    id = id or (1..1024).random().toLong()
    return id.toString()
}

fun idToDate(id: Long): ZonedDateTime {
    val delta = id shr(23)
    val date = 1554350400000 + delta
    val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(date),
        ZoneId.of("GMT+3")) // TODO
    return zonedDateTime
}