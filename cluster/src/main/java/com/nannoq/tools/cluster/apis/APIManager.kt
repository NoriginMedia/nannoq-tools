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

package com.nannoq.tools.cluster.apis

import com.nannoq.tools.cluster.CircuitBreakerUtils
import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.servicediscovery.Record
import io.vertx.servicediscovery.types.HttpEndpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * This class defines a wrapper for creating HTTP records that can be published on the eventbus,
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class APIManager @JvmOverloads constructor(
    private val vertx: Vertx,
    appConfig: JsonObject,
    private val apiHostProducer: APIHostProducer? = null
) {
    private val circuitBreakerMap: MutableMap<String, CircuitBreaker>
    private val circuitBreakerMessageConsumerMap: Map<String, MessageConsumer<JsonObject>>

    private val publicHost: String
    private val privateHost: String

    constructor(appConfig: JsonObject) :
            this(Vertx.currentContext().owner(), appConfig, null)

    constructor(appConfig: JsonObject, apiHostProducer: APIHostProducer) :
            this(Vertx.currentContext().owner(), appConfig, apiHostProducer)

    init {
        circuitBreakerMap = ConcurrentHashMap()
        circuitBreakerMessageConsumerMap = ConcurrentHashMap()

        publicHost = appConfig.getString("publicHost")
        privateHost = appConfig.getString("privateHost")

        vertx.deployVerticle(KillVerticle())
    }

    private inner class KillVerticle : AbstractVerticle() {
        @Throws(Exception::class)
        override fun stop(stopFuture: Future<Void>) {
            val unRegisterFutures = ArrayList<Future<*>>()

            circuitBreakerMessageConsumerMap.values.forEach {
                val future = Future.future<Void>()
                it.unregister(future)
                unRegisterFutures.add(future)

                logger.info("Unregistered API circuitbreaker Consumer: " + it.address())
            }

            CompositeFuture.all(unRegisterFutures).setHandler {
                when {
                    it.failed() -> stopFuture.fail(it.cause())
                    else -> stopFuture.complete()
                }
            }
        }
    }

    private fun prepareCircuitBreaker(path: String): CircuitBreaker {
        val existingCircuitBreaker = circuitBreakerMap[path]
        if (existingCircuitBreaker != null) return existingCircuitBreaker

        val circuitBreakerName = API_CIRCUIT_BREAKER_BASE + path
        val circuitBreaker = CircuitBreaker.create(circuitBreakerName, vertx,
                CircuitBreakerOptions()
                        .setMaxFailures(3)
                        .setTimeout(30000)
                        .setFallbackOnFailure(true)
                        .setResetTimeout(10000)
                        .setNotificationAddress(circuitBreakerName)
                        .setNotificationPeriod(60000L * 60 * 6))
                .openHandler { logger.info("$circuitBreakerName OPEN") }
                .halfOpenHandler { logger.info("$circuitBreakerName HALF-OPEN") }
                .closeHandler { logger.info("$circuitBreakerName CLOSED") }
        circuitBreaker.close()

        val apiConsumer = vertx.eventBus().consumer<JsonObject>(circuitBreakerName)
        apiConsumer.handler { message -> CircuitBreakerUtils.handleCircuitBreakerEvent(circuitBreaker, message) }

        circuitBreakerMap[path] = circuitBreaker

        return circuitBreaker
    }

    fun <T> performRequestWithCircuitBreaker(
        path: String,
        resultHandler: Handler<AsyncResult<T>>,
        handler: Handler<Future<T>>,
        fallback: (Throwable) -> Unit
    ) {
        CircuitBreakerUtils.performRequestWithCircuitBreaker(
                prepareCircuitBreaker(path), resultHandler, handler, fallback)
    }

    @JvmOverloads
    fun createInternalApiRecord(name: String, path: String, ssl: Boolean = true): Record {
        return HttpEndpoint.createRecord(name, ssl,
                apiHostProducer?.getInternalHost(name) ?: privateHost, if (ssl) 443 else 80, path, null)
    }

    @JvmOverloads
    fun createExternalApiRecord(name: String, path: String, ssl: Boolean = true): Record {
        return HttpEndpoint.createRecord(name, ssl,
                apiHostProducer?.getExternalHost(name) ?: publicHost, if (ssl) 443 else 80, path, null)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(APIManager::class.java.simpleName)

        private const val GENERIC_HTTP_REQUEST_CIRCUITBREAKER = "com.apis.generic.circuitbreaker"
        private const val API_CIRCUIT_BREAKER_BASE = "com.apis.circuitbreaker."

        fun <T> performRequestWithCircuitBreaker(
            resultHandler: Handler<AsyncResult<T>>,
            handler: Handler<Future<T>>,
            fallback: (Throwable) -> Unit
        ) {
            CircuitBreakerUtils.performRequestWithCircuitBreaker(
                    CircuitBreaker.create(GENERIC_HTTP_REQUEST_CIRCUITBREAKER, Vertx.currentContext().owner(),
                            CircuitBreakerOptions()
                                    .setMaxFailures(5)
                                    .setFallbackOnFailure(true)
                                    .setTimeout(5000L)
                                    .setNotificationAddress(GENERIC_HTTP_REQUEST_CIRCUITBREAKER)
                                    .setNotificationPeriod(60000L)),
                    resultHandler, handler, fallback)
        }
    }
}
