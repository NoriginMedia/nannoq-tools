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

package com.nannoq.tools.auth.webhandlers

import com.nannoq.tools.auth.AuthGlobals.GLOBAL_AUTHORIZATION
import com.nannoq.tools.auth.AuthUtils
import com.nannoq.tools.auth.utils.Authorization
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.addLogMessageToRequestLog
import io.vertx.core.Handler
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import java.util.concurrent.TimeUnit

/**
 * This class defines an auth handler for verifying jwts. It builds a request based on the method of the original client
 * request, and the model it is instantiated with. It accepts an optional domainIdentifier value.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class AuthHandler(private val TYPE: Class<*>, private val domainIdentifier: String, private val apiKey: String) : Handler<RoutingContext> {
    private val authUtils: AuthUtils = AuthUtils.instance!!

    override fun handle(routingContext: RoutingContext) {
        val processStartTime = System.nanoTime()
        val auth = routingContext.request().getHeader("Authorization")

        if (logger.isDebugEnabled) { addLogMessageToRequestLog(routingContext, "Starting auth for: " + auth!!) }

        when {
            auth != null ->
                when {
                    auth.startsWith("APIKEY ") -> {
                        when (auth.substring("APIKEY".length).trim { it <= ' ' }) {
                            apiKey -> {
                                addLogMessageToRequestLog(routingContext, "INFO: Google AUTH overriden by API KEY!")
                                setAuthProcessTime(routingContext, processStartTime)

                                routingContext.next()
                            }
                            else -> unAuthorized(routingContext, processStartTime)
                        }
                    }
                    auth.startsWith("Bearer") -> {
                        val token = auth.substring("Bearer".length).trim { it <= ' ' }

                        if (logger.isInfoEnabled) {
                            addLogMessageToRequestLog(routingContext, "Preparing request to auth backend...")
                        }

                        val request = routingContext.request()
                        val domain = routingContext.pathParam(domainIdentifier)
                        val authorization = Authorization(
                                request.rawMethod(), TYPE.simpleName, domain ?: GLOBAL_AUTHORIZATION)

                        authUtils.authenticateAndAuthorize(token, authorization, Handler {
                            when {
                                it.failed() -> {
                                    logger.error("Failure in Auth: " + Json.encodePrettily(authorization), it.cause())

                                    addLogMessageToRequestLog(routingContext, "Unauthorized!", it.cause())
                                    unAuthorized(routingContext, processStartTime)
                                }
                                else -> {
                                    setAuthProcessTime(routingContext, processStartTime)
                                    routingContext.put(AuthUtils.USER_IDENTIFIER, it.result().id)

                                    routingContext.next()
                                }
                            }
                        })
                    }
                    else -> unAuthorized(routingContext, processStartTime)
                }
            else -> unAuthorized(routingContext, processStartTime)
        }
    }

    private fun setAuthProcessTime(routingContext: RoutingContext, initialTime: Long) {
        val processTimeInNano = System.nanoTime() - initialTime
        routingContext.response().putHeader(AUTH_PROCESS_TIME,
                TimeUnit.NANOSECONDS.toMillis(processTimeInNano).toString())
    }

    private fun unAuthorized(routingContext: RoutingContext, processStartTime: Long) {
        setAuthProcessTime(routingContext, processStartTime)

        routingContext.fail(401)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthHandler::class.java.simpleName)

        const val AUTH_PROCESS_TIME = "X-Auth-Time-To-Process"
    }
}
