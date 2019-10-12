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

package com.nannoq.tools.cluster

import com.nannoq.tools.cluster.services.ServiceManager
import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.serviceproxy.ServiceException

/**
 * This class defines various helpers for circuitbreakers.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
object CircuitBreakerUtils {
    private val logger = LoggerFactory.getLogger(CircuitBreakerUtils::class.java.simpleName)

    fun <T> performRequestWithCircuitBreaker(
        circuitBreaker: CircuitBreaker,
        resultHandler: Handler<AsyncResult<T>>,
        handler: Handler<Promise<T>>,
        backup: (Throwable) -> Unit
    ) {
        val result = Promise.promise<T>()
        result.future().setHandler {
            logger.debug("Received " + circuitBreaker.name() + " Result: " + it.succeeded())

            when {
                it.succeeded() -> resultHandler.handle(Future.succeededFuture(it.result()))
                else -> {
                    logger.debug("Failed: " + it.cause())

                    when {
                        it.cause() is ServiceException -> {
                            ServiceManager.handleResultFailed(it.cause())

                            resultHandler.handle(Future.failedFuture(it.cause()))
                        }
                        else -> {
                            if (it.cause() != null && it.cause().message == "operation timeout") {
                                logger.error(circuitBreaker.name() + " Timeout, failures: " +
                                        circuitBreaker.failureCount() + ", state: " + circuitBreaker.state().name)
                            }

                            backup(it.cause())
                        }
                    }
                }
            }
        }

        circuitBreaker.executeAndReport(result, handler)
    }

    /**
     * For use with debugging circuitbreaker operation.
     *
     * @param circuitBreaker CircuitBreaker
     * @param serviceEvent Message of JsonObject
     */
    fun handleCircuitBreakerEvent(circuitBreaker: CircuitBreaker, serviceEvent: Message<JsonObject>) {
        /*logger.trace("Event for: "  + circuitBreaker.name());

        MultiMap headers = serviceEvent.headers();
        JsonObject body = serviceEvent.body();

        logger.trace("CircuitBreaker Event:\n" + Json.encodePrettily(serviceEvent) +
                "\nHeaders:\n" + Json.encodePrettily(headers) +
                "\nBody:\n" + Json.encodePrettily(body));*/
    }
}
