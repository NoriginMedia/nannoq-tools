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

package com.nannoq.tools.auth.webhandlers

import com.nannoq.tools.auth.AuthGlobals.VALIDATION_REQUEST
import com.nannoq.tools.auth.models.VerifyResult
import com.nannoq.tools.auth.services.VerificationService
import com.nannoq.tools.auth.utils.AuthFutures.authFail
import com.nannoq.tools.auth.utils.AuthFutures.getToken
import com.nannoq.tools.auth.utils.AuthFutures.verifyToken
import com.nannoq.tools.auth.utils.Authorization
import com.nannoq.tools.web.responsehandlers.ResponseLogHandler.Companion.BODY_CONTENT_TAG
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.DecodeException
import io.vertx.core.json.Json
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import io.vertx.serviceproxy.ServiceException
import java.util.Base64
import java.util.function.Consumer

/**
 * This class defines a Handler implementation that receives all traffic for endpoints that handle JWT reception, e.g.
 * verification and authorization.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class JWTReceiver @JvmOverloads constructor(private val verifier: VerificationService, AUTHORIZATION_TYPE_HEADER: String? = null) : Handler<RoutingContext> {
    private val AUTHORIZATION_TYPE_HEADER: String

    init {
        when (AUTHORIZATION_TYPE_HEADER) {
            null -> this.AUTHORIZATION_TYPE_HEADER = "X-Authorization-Type"
            else -> this.AUTHORIZATION_TYPE_HEADER = AUTHORIZATION_TYPE_HEADER
        }
    }

    override fun handle(routingContext: RoutingContext) {
        val success = Handler<VerifyResult> {
            routingContext.put(BODY_CONTENT_TAG, Json.encode(it))
            routingContext.response().statusCode = 200
            routingContext.next()
        }

        getToken(routingContext).compose({ token ->
            verifyToken(verifier, token).compose({ claims ->
                authorizeRequest(claims, routingContext).compose(success, authFail<Any>(routingContext))
            }, authFail<Any>(routingContext))
        }, authFail<Any>(routingContext))
    }

    private fun authorizeRequest(claims: Jws<Claims>, routingContext: RoutingContext): Future<VerifyResult> {
        val idFuture = Future.future<VerifyResult>()
        val authorization = routingContext.request().getHeader(AUTHORIZATION_TYPE_HEADER)

        logger.info("Incoming Auth Json Base64 is: $authorization")

        try {
            val authorizationPOJO = when (authorization) {
                null -> Authorization.global()
                else -> {
                    val json = String(Base64.getDecoder().decode(authorization))

                    logger.info("Incoming Auth Json is: $json")

                    Json.decodeValue<Authorization>(json, Authorization::class.java)
                }
            }

            when (VALIDATION_REQUEST) {
                authorizationPOJO.domainIdentifier -> idFuture.complete(VerifyResult(claims.body.subject))
                else -> try {
                    checkAuthorization(authorizationPOJO, claims, Handler {
                        when {
                            it.failed() -> idFuture.fail(SecurityException("You are not authorized for this action!"))
                            else -> idFuture.complete(VerifyResult(claims.body.subject))
                        }
                    })
                } catch (e: IllegalAccessException) {
                    idFuture.fail(e)
                }
            }
        } catch (e: DecodeException) {
            idFuture.fail(SecurityException("You are not authorized for this action, illegal AuthTypeToken!"))
        }

        return idFuture
    }

    @Throws(IllegalAccessException::class)
    private fun checkAuthorization(
        authorization: Authorization,
        claims: Jws<Claims>,
        completer: Handler<AsyncResult<Boolean>>
    ) {
        verifier.verifyAuthorization(claims, authorization, completer)
    }

    fun revoke(routingContext: RoutingContext) {
        val success = Consumer<Void> {
            routingContext.response().statusCode = 204
            routingContext.next()
        }

        getToken(routingContext).compose({ token ->
            revokeToken(token).compose(Handler {
                success.accept(it)
            }, authFail<Any>(routingContext))
        }, authFail<Any>(routingContext))
    }

    private fun revokeToken(token: String): Future<Void> {
        val revokeFuture = Future.future<Void>()

        verifier.revokeToken(token) { revokeResult ->
            when {
                revokeResult.failed() ->
                    when {
                        revokeResult.cause() is ServiceException -> revokeFuture.fail(revokeResult.cause())
                        else -> revokeFuture.fail(SecurityException("Unable to revoke token..."))
                    }
                else -> revokeFuture.complete()
            }
        }

        return revokeFuture
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JWTReceiver::class.java.simpleName)
    }
}
