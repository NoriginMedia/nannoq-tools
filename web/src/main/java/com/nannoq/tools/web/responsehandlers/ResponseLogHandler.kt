/*
 * MIT License
 *
 * Copyright (c) 2017 Anders Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.nannoq.tools.web.responsehandlers

import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.REQUEST_ID_TAG
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.REQUEST_LOG_TAG
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.REQUEST_PROCESS_TIME_TAG
import io.vertx.core.Handler
import io.vertx.core.json.DecodeException
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import java.util.concurrent.TimeUnit

/**
 * This interface defines the ResponseLogHandler. It starts the logging process, to be concluded by the
 * responseloghandler.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class ResponseLogHandler : Handler<RoutingContext> {
    override fun handle(routingContext: RoutingContext) {
        val uniqueToken = routingContext.get<String>(REQUEST_ID_TAG)
        val statusCode = routingContext.response().statusCode
        val body = routingContext.get<Any>(BODY_CONTENT_TAG)
        val debug = routingContext.get<Any>(DEBUG_INFORMATION_OBJECT)
        val processStartTime = routingContext.get<Long>(REQUEST_PROCESS_TIME_TAG)
        val processTimeInNano = System.nanoTime() - processStartTime
        val totalProcessTime = TimeUnit.NANOSECONDS.toMillis(processTimeInNano)

        routingContext.response().putHeader("X-Nannoq-Debug", uniqueToken)
        routingContext.response().putHeader("X-Internal-Time-To-Process", totalProcessTime.toString())

        if (statusCode >= 400 || statusCode == 202) {
            addCorsHeaders(routingContext)
        }

        val sb = buildLogs(routingContext, statusCode, uniqueToken, body, debug)

        when (body) {
            null -> routingContext.response().end()
            else -> routingContext.response().end((body as? String)?.toString() ?: Json.encode(body))
        }

        outputLog(statusCode, sb)
    }

    private fun addCorsHeaders(routingContext: RoutingContext) {
        routingContext.response().putHeader("Access-Control-Allow-Origin", "*")
        routingContext.response().putHeader("Access-Control-Allow-Credentials", "false")
        routingContext.response().putHeader(
                "Access-Control-Allow-Methods",
                "POST, GET, PUT, DELETE, OPTIONS")
        routingContext.response().putHeader(
                "Access-Control-Allow-Headers",
                "DNT,Authorization,X-Real-IP,X-Forwarded-For,Keep-Alive,User-Agent," +
                        "X-Requested-With,If-None-Match,Cache-Control,Content-Type")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResponseLogHandler::class.java.simpleName)

        const val BODY_CONTENT_TAG = "bodyContent"
        const val DEBUG_INFORMATION_OBJECT = "debugInfo"

        fun buildLogs(
            routingContext: RoutingContext,
            statusCode: Int,
            uniqueToken: String,
            body: Any?,
            debug: Any?
        ): StringBuffer {
            val stringBuilder = routingContext.get<StringBuffer>(REQUEST_LOG_TAG)
            val sb = stringBuilder ?: StringBuffer()

            sb.append("\n--- ").append("Logging Frame End: ").append(uniqueToken).append(" ---\n")
            sb.append("\n--- ").append("Request Frame : ").append(statusCode).append(" ---\n")
            sb.append("\nHeaders:\n")

            routingContext.request().headers().forEach(appendRequestHeaders(sb))

            var bodyObject: JsonObject? = null
            var requestBody: String? = null

            try {
                requestBody = routingContext.bodyAsString

                if (routingContext.body != null && routingContext.body.bytes.isNotEmpty()) {
                    try {
                        bodyObject = JsonObject(requestBody)
                    } catch (ignored: DecodeException) { }
                }
            } catch (e: Exception) {
                logger.debug("Parse exception!", e)
            }

            sb.append("\nRequest Body:\n").append(if (bodyObject == null) requestBody else bodyObject.encodePrettily())
            sb.append("\n--- ").append("End Request: ").append(uniqueToken).append(" ---")
            sb.append("\n--- ").append("Debug Info: ").append(uniqueToken).append(" ---")
            sb.append("\n").append(if (debug == null)
                null
            else (debug as? String)?.toString() ?: Json.encodePrettily(debug)).append("\n")
            sb.append("\n--- ").append("End Debug Info: ").append(uniqueToken).append(" ---")
            sb.append("\n--- ").append("Response: ").append(uniqueToken).append(" ---")
            sb.append("\nHeaders:\n")

            routingContext.response().headers().forEach(appendResponseHeaders(sb))

            sb.append("\nResponse Body:\n").append(bodyToAppend(body))
            sb.append("\n--- ").append("Request Frame : ").append(statusCode).append(" ---\n")
            sb.append("\n--- ").append("End Request Logging: ").append(uniqueToken).append(" ---\n")

            return sb
        }

        private fun appendResponseHeaders(sb: StringBuffer): (MutableMap.MutableEntry<String, String>) -> Unit = { sb.append(it.key).append(" : ").append(it.value).append("\n") }

        private fun bodyToAppend(body: Any?): String? {
            return if (body == null)
                null
            else (body as? String)?.toString() ?: Json.encodePrettily(body)
        }

        private fun appendRequestHeaders(sb: StringBuffer): (MutableMap.MutableEntry<String, String>) -> Unit {
            return {
                when {
                    filteredHeader(it) -> sb.append(it.key).append(" : ").append("[FILTERED]").append("\n")
                    else -> sb.append(it.key).append(" : ").append(it.value).append("\n")
                }
            }
        }

        private fun filteredHeader(it: MutableMap.MutableEntry<String, String>) =
                it.key.equals("Authorization", ignoreCase = true) ||
                        it.key.equals("X-Forwarded-For", ignoreCase = true)

        private fun outputLog(statusCode: Int, sb: StringBuffer) {
            when {
                statusCode in 200..399 ->
                    if (logger.isDebugEnabled) {
                        logger.info(sb.toString())
                    }
                statusCode in 400..499 -> logger.warn(sb.toString())
                statusCode >= 500 -> logger.error(sb.toString())
                else ->
                    if (logger.isDebugEnabled) {
                        logger.debug(sb.toString())
                    }
            }
        }
    }
}