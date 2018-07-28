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
 */

package com.nannoq.tools.auth.utils

import com.nannoq.tools.auth.services.VerificationService
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.addLogMessageToRequestLog
import com.nannoq.tools.web.responsehandlers.ResponseLogHandler.Companion.BODY_CONTENT_TAG
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import io.vertx.serviceproxy.ServiceException

/**
 * This class defines a set of static methods for performing various auth related futures.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
object AuthFutures {
    private val logger = LoggerFactory.getLogger(AuthFutures::class.java.simpleName)

    fun getToken(routingContext: RoutingContext): Future<String> {
        val tokenFuture = Future.future<String>()
        val authentication = routingContext.request().getHeader(HttpHeaders.AUTHORIZATION)

        when {
            authentication != null ->
                when {
                    authentication.startsWith("Bearer ") ->
                        tokenFuture.complete(authentication.substring("Bearer".length).trim({ it <= ' ' }))
                    else -> tokenFuture.fail(IllegalArgumentException("Auth does not start with Bearer!"))
                }
            else -> tokenFuture.fail(IllegalArgumentException("Auth is null!"))
        }

        return tokenFuture
    }

    fun verifyToken(verifier: VerificationService, token: String): Future<Jws<Claims>> {
        val claimsFuture = Future.future<Jws<Claims>>()

        verifier.verifyToken(token, { resultHandler ->
            when {
                resultHandler.failed() ->
                    when {
                        resultHandler.cause() is ServiceException -> claimsFuture.fail(resultHandler.cause())
                        else -> claimsFuture.fail(SecurityException("Could not verify JWT..."))
                    }
                else -> claimsFuture.complete(resultHandler.result())
            }
        })

        return claimsFuture
    }

    fun <U> denyRequest(routingContext: RoutingContext): Future<U> {
        return Future.future<U>().setHandler { handler -> routingContext.response().setStatusCode(400).end() }
    }

    fun <U> authFail(routingContext: RoutingContext): Future<U> {
        return Future.future<U>().setHandler { handler -> doAuthFailure(routingContext, handler) }
    }

    fun <U> doAuthFailure(routingContext: RoutingContext, handler: AsyncResult<U>) {
        val errorMessage: String

        errorMessage = if (handler.cause() is ServiceException) {
            val se = handler.cause() as ServiceException

            "AUTH ERROR: Authorization Cause is: " + se.message + " : " + se.debugInfo.encodePrettily()
        } else {
            "AUTH ERROR: Authorization Cause is: " + handler.cause().message
        }

        addLogMessageToRequestLog(routingContext, errorMessage)

        routingContext.put(BODY_CONTENT_TAG, JsonObject().put("auth_error", errorMessage))

        unAuthorized(routingContext)
    }

    fun <U> authFailRedirect(routingContext: RoutingContext): Future<U> {
        return authFailRedirect(routingContext, null)
    }

    fun <U> authFailRedirect(routingContext: RoutingContext, location: String?): Future<U> {
        return Future.future<U>().setHandler { handler -> doAuthFailureRedirect(routingContext, handler, location) }
    }

    fun <U> doAuthFailureRedirect(routingContext: RoutingContext, handler: AsyncResult<U>) {
        doAuthFailureRedirect(routingContext, handler, null)
    }

    fun <U> doAuthFailureRedirect(routingContext: RoutingContext, handler: AsyncResult<U>, location: String?) {
        val errorMessage: String

        errorMessage = if (handler.cause() is ServiceException) {
            val se = handler.cause() as ServiceException

            "AUTH ERROR: Authorization Cause is: " + se.message + " : " + se.debugInfo.encodePrettily()
        } else {
            "AUTH ERROR: Authorization Cause is: " + handler.cause().message
        }

        addLogMessageToRequestLog(routingContext, errorMessage)

        routingContext.put(BODY_CONTENT_TAG, JsonObject().put("auth_error", errorMessage))

        when (location) {
            null -> unAuthorizedRedirect(routingContext, handler.cause().message ?: "Error")
            else -> unAuthorizedRedirect(routingContext, location)
        }
    }

    private fun unAuthorized(routingContext: RoutingContext) {
        routingContext.response().statusCode = 401
        routingContext.next()
    }

    private fun unAuthorizedRedirect(routingContext: RoutingContext, location: String) {
        addLogMessageToRequestLog(routingContext, "Unauthorized!")

        routingContext.response().setStatusCode(302).putHeader("Location", location).end()
    }

    fun <U, T> authFail(resultHandler: Handler<AsyncResult<T>>): Future<U> {
        return Future.future<U>().setHandler { handler -> doAuthFailure(handler, resultHandler) }
    }

    fun <U, T> doAuthFailure(handler: AsyncResult<U>, resultHandler: Handler<AsyncResult<T>>) {
        val errorMessage = "AUTH ERROR: Authentication Cause is: " + handler.cause().message

        when {
            handler.cause() is ServiceException -> resultHandler.handle(Future.failedFuture(handler.cause()))
            else -> resultHandler.handle(ServiceException.fail(500, errorMessage))
        }
    }
}
