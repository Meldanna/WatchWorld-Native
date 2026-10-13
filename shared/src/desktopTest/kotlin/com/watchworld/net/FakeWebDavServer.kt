package com.watchworld.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections

/** 一条被假上游记录下来的 WebDAV 请求。 */
data class RecordedWebDavRequest(
    val method: String,
    val path: String,
    val authorization: String?,
    val depth: String?,
    val body: String,
)

/**
 * 进程内假 WebDAV 服务（`com.sun.net.httpserver`），内存里存文件。
 *
 * 为什么不用 mock 客户端：PROPFIND/PUT/GET/DELETE/MKCOL 的方法名、`Depth` 头、
 * Basic 认证、以及路径拼接（`syncRoot` / `safeFileName`）全在这一层，
 * mock 掉就漏掉一半。
 */
class FakeWebDavServer(
    private val propfindStatus: Int = 207,
    private val putStatus: Int = 201,
    private val deleteStatus: Int = 204,
    private val mkcolStatus: Int = 201,
) {
    val requests: MutableList<RecordedWebDavRequest> = Collections.synchronizedList(mutableListOf())
    private val store: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/dav") { exchange -> handle(exchange) }
        server.executor = null
        server.start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}/dav"

    fun stored(path: String): String? = store[path]

    fun store(path: String, body: String) {
        store[path] = body
    }

    fun stop() = server.stop(0)

    private fun handle(exchange: HttpExchange) {
        val method = exchange.requestMethod.uppercase()
        val path = exchange.requestURI.path
        val body = exchange.requestBody.readBytes().decodeToString()
        requests += RecordedWebDavRequest(
            method = method,
            path = path,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            depth = exchange.requestHeaders.getFirst("Depth"),
            body = body,
        )
        when (method) {
            "PROPFIND" -> respond(exchange, propfindStatus, PROPFIND_BODY)
            "PUT" -> {
                store[path] = body
                respond(exchange, putStatus, "")
            }
            "GET" -> {
                val value = store[path]
                if (value == null) respond(exchange, 404, "not found") else respond(exchange, 200, value)
            }
            "DELETE" -> {
                store.remove(path)
                respond(exchange, deleteStatus, "")
            }
            "MKCOL" -> {
                if (store.containsKey(path)) {
                    respond(exchange, 405, "")
                } else {
                    store[path] = ""
                    respond(exchange, mkcolStatus, "")
                }
            }
            else -> respond(exchange, 405, "")
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        if (bytes.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        exchange.close()
    }

    companion object {
        const val PROPFIND_BODY: String = """<?xml version="1.0"?><D:multistatus xmlns:D="DAV:"/>"""
    }
}
