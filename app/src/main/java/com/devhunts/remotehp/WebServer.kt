package com.devhunts.remotehp

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Collections

/**
 * Serves the control dashboard over HTTP and streams screen frames /
 * receives control commands over a single WebSocket.
 */
class WebServer(
    port: Int,
    private val dashboardHtml: ByteArray,
    private val status: StatusProvider? = null
) : NanoWSD(port) {

    private val clients = Collections.synchronizedSet(mutableSetOf<Client>())

    override fun serveHttp(session: IHTTPSession): Response {
        if (session.uri == "/status") {
            val json = status?.json() ?: "{}"
            val r = newFixedLengthResponse(Response.Status.OK, "application/json", json)
            r.addHeader("Cache-Control", "no-store")
            return r
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html",
            ByteArrayInputStream(dashboardHtml),
            dashboardHtml.size.toLong()
        )
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = Client(handshake)

    fun broadcastFrame(bytes: ByteArray) {
        synchronized(clients) {
            val it = clients.iterator()
            while (it.hasNext()) {
                val c = it.next()
                try {
                    c.send(bytes)
                } catch (_: IOException) {
                    it.remove()
                }
            }
        }
    }

    inner class Client(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            clients.add(this)
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            clients.remove(this)
        }

        override fun onMessage(message: WebSocketFrame) {
            try {
                CommandHandler.handle(message.textPayload)
            } catch (_: Exception) {
            }
        }

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException?) {
            clients.remove(this)
        }
    }
}
