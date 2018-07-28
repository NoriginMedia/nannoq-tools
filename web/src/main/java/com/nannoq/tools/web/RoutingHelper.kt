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

package com.nannoq.tools.web

import com.nannoq.tools.web.requestHandlers.RequestLogHandler
import com.nannoq.tools.web.responsehandlers.ResponseLogHandler
import com.nannoq.tools.web.responsehandlers.ResponseLogHandler.Companion.BODY_CONTENT_TAG
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.ResponseContentTypeHandler
import io.vertx.ext.web.handler.ResponseTimeHandler
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors.*

/**
 * This class contains helper methods for routing requests.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
object RoutingHelper {
    private val logger = LoggerFactory.getLogger(RoutingHelper::class.java.simpleName)

    private const val DATABASE_PROCESS_TIME = "X-Database-Time-To-Process"

    val requestLogger = RequestLogHandler()
    val responseLogger = ResponseLogHandler()

    private val bodyHandler = BodyHandler.create().setMergeFormAttributes(true)
    private val timeOutHandler = Handler<RoutingContext> {
        it.vertx().setTimer(9000L, { time -> if (!it.request().isEnded) it.fail(503) })

        it.next()
    }

    private val responseContentTypeHandler = ResponseContentTypeHandler.create()
    private val responseTimeHandler = ResponseTimeHandler.create()

    fun setStatusCode(code: Int, routingContext: RoutingContext,
                      initialProcessTime: Long) {
        setDatabaseProcessTime(routingContext, initialProcessTime)
        routingContext.response().statusCode = code
    }

    fun setStatusCodeAndAbort(code: Int, routingContext: RoutingContext,
                              initialProcessTime: Long) {
        setDatabaseProcessTime(routingContext, initialProcessTime)
        routingContext.fail(code)
    }

    fun setStatusCodeAndContinue(code: Int, routingContext: RoutingContext) {
        routingContext.response().statusCode = code
        routingContext.next()
    }

    fun setStatusCodeAndContinue(code: Int, routingContext: RoutingContext,
                                 initialProcessTime: Long) {
        setDatabaseProcessTime(routingContext, initialProcessTime)
        routingContext.response().statusCode = code
        routingContext.next()
    }

    private fun setDatabaseProcessTime(routingContext: RoutingContext, initialTime: Long) {
        val processTimeInNano = System.nanoTime() - initialTime
        routingContext.response().putHeader(DATABASE_PROCESS_TIME,
                TimeUnit.NANOSECONDS.toMillis(processTimeInNano).toString())
    }

    fun routeWithAuth(routeProducer: Supplier<Route>, authHandler: Handler<RoutingContext>,
                      routeSetter: Consumer<Supplier<Route>>) {
        routeWithAuth(routeProducer, authHandler, null, routeSetter)
    }

    fun routeWithAuth(routeProducer: Supplier<Route>, authHandler: Handler<RoutingContext>,
                      finallyHandler: Handler<RoutingContext>?,
                      routeSetter: Consumer<Supplier<Route>>) {
        prependStandards(routeProducer)
        routeProducer.get().handler(authHandler)
        routeSetter.accept(routeProducer)
        appendStandards(routeProducer, finallyHandler)
    }

    fun routeWithBodyHandlerAndAuth(routeProducer: Supplier<Route>, authHandler: Handler<RoutingContext>,
                                    routeSetter: Consumer<Supplier<Route>>) {
        routeWithBodyHandlerAndAuth(routeProducer, authHandler, null, routeSetter)
    }

    fun routeWithBodyHandlerAndAuth(routeProducer: Supplier<Route>, authHandler: Handler<RoutingContext>,
                                    finallyHandler: Handler<RoutingContext>?,
                                    routeSetter: Consumer<Supplier<Route>>) {
        prependStandards(routeProducer)
        routeProducer.get().handler(bodyHandler)
        routeProducer.get().handler(authHandler)
        routeSetter.accept(routeProducer)
        appendStandards(routeProducer, finallyHandler)
    }

    private fun prependStandards(routeProducer: Supplier<Route>) {
        routeProducer.get().handler(responseTimeHandler)
        routeProducer.get().handler(timeOutHandler)
        routeProducer.get().handler(responseContentTypeHandler)
        routeProducer.get().handler(requestLogger)
    }

    private fun appendStandards(routeProducer: Supplier<Route>, finallyHandler: Handler<RoutingContext>?) {
        routeProducer.get().failureHandler { handleErrors(it) }
        if (finallyHandler != null) routeProducer.get().handler(finallyHandler)
        routeProducer.get().handler(responseLogger)
    }

    fun routeWithLogger(routeProducer: Supplier<Route>,
                        routeSetter: Consumer<Supplier<Route>>) {
        routeWithLogger(routeProducer, null, routeSetter)
    }

    fun routeWithBodyAndLogger(routeProducer: Supplier<Route>,
                               routeSetter: Consumer<Supplier<Route>>) {
        routeProducer.get().handler(responseTimeHandler)
        routeProducer.get().handler(timeOutHandler)
        routeProducer.get().handler(responseContentTypeHandler)
        routeProducer.get().handler(requestLogger)
        routeProducer.get().handler(bodyHandler)
        routeSetter.accept(routeProducer)
        routeProducer.get().failureHandler { handleErrors(it) }
        routeProducer.get().handler(responseLogger)
    }

    fun routeWithLogger(routeProducer: Supplier<Route>,
                        finallyHandler: Handler<RoutingContext>?,
                        routeSetter: Consumer<Supplier<Route>>) {
        routeProducer.get().handler(responseTimeHandler)
        routeProducer.get().handler(timeOutHandler)
        routeProducer.get().handler(responseContentTypeHandler)
        routeProducer.get().handler(requestLogger)
        routeSetter.accept(routeProducer)
        routeProducer.get().failureHandler { handleErrors(it) }
        if (finallyHandler != null) routeProducer.get().handler(finallyHandler)
        routeProducer.get().handler(responseLogger)
    }

    fun handleErrors(routingContext: RoutingContext) {
        val statusCode = routingContext.statusCode()

        routingContext.response().statusCode = if (statusCode != -1) statusCode else 500

        routingContext.next()
    }

    fun denyQuery(routingContext: RoutingContext): Boolean {
        val query = routingContext.request().query()

        when {
            query != null && !routingContext.request().rawMethod().equals("GET", ignoreCase = true) -> return true
            query != null -> {
                val queryMap = splitQuery(query)

                when {
                    queryMap.size > 1 || queryMap.size == 1 && queryMap["projection"] == null -> {
                        routingContext.put(BODY_CONTENT_TAG, JsonObject()
                                .put("query_error", "No query accepted for this route"))
                        routingContext.fail(400)

                        return true
                    }
                    else -> {
                        routingContext.put(BODY_CONTENT_TAG, JsonObject()
                                .put("query_error", "Cannot parse this query string, are you sure it is in UTF-8?"))
                        routingContext.fail(400)
                    }
                }
            }
        }

        return false
    }

    fun splitQuery(query: String): MutableMap<String, List<String>> {
        val decoded: String

        try {
            decoded = URLDecoder.decode(query, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            logger.error(e)

            return mutableMapOf()
        }

        return Arrays.stream<String>(decoded.split("&".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray())
                .map({ splitQueryParameter(it) })
                .collect(groupingBy(
                        { it.key },
                        { mutableMapOf<String, List<String>>() },
                        mapping({ it.value }, toList())))
    }

    private fun splitQueryParameter(it: String): AbstractMap.SimpleImmutableEntry<String, String> {
        val idx = it.indexOf("=")
        val key = if (idx > 0) it.substring(0, idx) else it
        val value = if (idx > 0 && it.length > idx + 1) it.substring(idx + 1) else null

        return AbstractMap.SimpleImmutableEntry<String, String>(key, value)
    }
}
