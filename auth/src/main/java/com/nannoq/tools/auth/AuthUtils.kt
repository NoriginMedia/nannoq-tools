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

package com.nannoq.tools.auth

import com.nannoq.tools.auth.models.AuthPackage
import com.nannoq.tools.auth.models.TokenContainer
import com.nannoq.tools.auth.models.UserProfile
import com.nannoq.tools.auth.models.VerifyResult
import com.nannoq.tools.auth.services.AuthenticationService
import com.nannoq.tools.auth.services.VerificationService
import com.nannoq.tools.auth.utils.Authorization
import com.nannoq.tools.cluster.CircuitBreakerUtils
import com.nannoq.tools.cluster.apis.APIManager
import com.nannoq.tools.cluster.services.ServiceManager
import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.serviceproxy.ServiceException
import java.util.*

/**
 * This class is used for doing most auth options from a client perspective. It has evnetbus and http logic, with retry
 * backups by circuitbreaker.
 *
 * @author Anders Mikkelsen
 * @version 3/3/16
 */
class AuthUtils private constructor(private val vertx: Vertx, appConfig: JsonObject) {
    private var AUTH_TOKEN_ENDPOINT = "/auth/api/oauth2/auth/convert"
    private var AUTH_VERIFY_ENDPOINT = "/auth/api/oauth2/verify"

    private var authAuthCircuitBreaker: CircuitBreaker? = null
    private var authVerifyCircuitBreaker: CircuitBreaker? = null

    private var authCircuitBreakerEvents: MessageConsumer<JsonObject>? = null
    private var verifyCircuitBreakerEvents: MessageConsumer<JsonObject>? = null
    private val apiManager: APIManager

    private val authenticationService: Future<AuthenticationService>
        get() {
            val authenticationServiceFuture = Future.future<AuthenticationService>()

            ServiceManager.getInstance().consumeService(AuthenticationService::class.java, Handler { fetchResult ->
                if (fetchResult.failed()) {
                    logger.error("Failed Auth Service fetch...")

                    authenticationServiceFuture.fail(fetchResult.cause())
                } else {
                    authenticationServiceFuture.complete(fetchResult.result())
                }
            })

            return authenticationServiceFuture
        }

    private val verificationService: Future<VerificationService>
        get() {
            val verificationServiceFuture = Future.future<VerificationService>()

            ServiceManager.getInstance().consumeService(VerificationService::class.java, Handler { fetchResult ->
                if (fetchResult.failed()) {
                    logger.error("Failed Verification Service fetch...")

                    verificationServiceFuture.fail(fetchResult.cause())
                } else {
                    verificationServiceFuture.complete(fetchResult.result())
                }
            })

            return verificationServiceFuture
        }

    private constructor(appConfig: JsonObject = Vertx.currentContext().config()) : this(Vertx.currentContext().owner(), appConfig)

    init {

        logger.info("Initializing AuthUtils...")

        apiManager = APIManager(vertx, appConfig)
        AUTH_TOKEN_ENDPOINT = appConfig.getString("authTokenEndpoint")
        AUTH_VERIFY_ENDPOINT = appConfig.getString("authVerifyEndpoint")

        prepareCircuitBreakers()
        prepareListeners()

        Runtime.getRuntime().addShutdownHook(Thread {
            authCircuitBreakerEvents!!.unregister()
            verifyCircuitBreakerEvents!!.unregister()
        })

        logger.info("AuthUtils initialized...")
    }

    private fun prepareCircuitBreakers() {
        authAuthCircuitBreaker = CircuitBreaker.create(AUTH_AUTH_CIRCUTBREAKER_NAME, vertx,
                CircuitBreakerOptions()
                        .setMaxFailures(3)
                        .setTimeout(3000)
                        .setFallbackOnFailure(true)
                        .setResetTimeout(10000)
                        .setNotificationAddress(AUTH_AUTH_CIRCUTBREAKER_NAME)
                        .setNotificationPeriod(60000L * 60 * 6))
                .openHandler { v -> logger.info("$AUTH_AUTH_CIRCUTBREAKER_NAME OPEN") }
                .halfOpenHandler { v -> logger.info("$AUTH_AUTH_CIRCUTBREAKER_NAME HALF-OPEN") }
                .closeHandler { v -> logger.info("$AUTH_AUTH_CIRCUTBREAKER_NAME CLOSED") }
        authAuthCircuitBreaker!!.close()

        authVerifyCircuitBreaker = CircuitBreaker.create(AUTH_VERIFY_CIRCUTBREAKER_NAME, vertx,
                CircuitBreakerOptions()
                        .setMaxFailures(3)
                        .setTimeout(1000)
                        .setFallbackOnFailure(true)
                        .setResetTimeout(10000)
                        .setNotificationAddress(AUTH_VERIFY_CIRCUTBREAKER_NAME)
                        .setNotificationPeriod(60000L * 60 * 6))
                .openHandler { v -> logger.info("$AUTH_VERIFY_CIRCUTBREAKER_NAME OPEN") }
                .halfOpenHandler { v -> logger.info("$AUTH_AUTH_CIRCUTBREAKER_NAME HALF-OPEN") }
                .closeHandler { v -> logger.info("$AUTH_VERIFY_CIRCUTBREAKER_NAME CLOSED") }
        authVerifyCircuitBreaker!!.close()
    }

    private fun prepareListeners() {
        authCircuitBreakerEvents = vertx.eventBus().consumer(AUTH_AUTH_CIRCUTBREAKER_NAME) {
            CircuitBreakerUtils.handleCircuitBreakerEvent(authAuthCircuitBreaker!!, it) }

        verifyCircuitBreakerEvents = vertx.eventBus().consumer(AUTH_VERIFY_CIRCUTBREAKER_NAME) {
            CircuitBreakerUtils.handleCircuitBreakerEvent(authVerifyCircuitBreaker!!, it) }
    }

    @Fluent
    fun convertExternalToken(token: String?, provider: String, resultHandler: Handler<AsyncResult<AuthPackage>>): AuthUtils {
        logger.debug("Auth request ready for: $provider")

        if (token == null) {
            logger.error("Token cannot be null!")

            resultHandler.handle(Future.failedFuture("Token cannot be null!"))
        } else {
            val backup : (Throwable) -> Unit = {
                httpAuthBackUp(token, provider, Handler { httpResult ->
                    logger.debug("Launching http backup...")

                    if (httpResult.failed()) {
                        resultHandler.handle(Future.failedFuture<AuthPackage>(httpResult.cause()))
                    } else {
                        resultHandler.handle(Future.succeededFuture(httpResult.result()))
                    }
                })
            }

            authenticationService.compose({ service ->
                logger.debug("Running Eventbus Auth...")

                CircuitBreakerUtils.performRequestWithCircuitBreaker(authAuthCircuitBreaker!!, resultHandler, Handler {
                    attemptAuthConversionOnEventBus(it, service, token, provider) }, backup)
            }, Future.future<Any>().setHandler { failure -> backup(failure.cause()) })
        }

        return this
    }

    private fun attemptAuthConversionOnEventBus(authFuture: Future<AuthPackage>,
                                                authenticationService: AuthenticationService,
                                                token: String?, provider: String) {
        authenticationService.createJwtFromProvider(token!!, provider, Handler { conversionResult ->
            if (authFuture.isComplete) {
                logger.error("Ignoring result, authFuture already completed: " + conversionResult.cause())
            } else {
                if (conversionResult.failed()) {
                    logger.error("Conversion failed!")

                    if (conversionResult.cause() is ServiceException) {
                        ServiceManager.handleResultFailed(conversionResult.cause())
                        authFuture.complete(null)
                    } else {
                        authFuture.fail(conversionResult.cause())
                    }
                } else {
                    logger.debug("Conversion ok, returning result...")

                    authFuture.complete(conversionResult.result())
                }
            }
        })
    }

    private fun httpAuthBackUp(token: String?, provider: String,
                               resultHandler: Handler<AsyncResult<AuthPackage>>) {
        logger.debug("Running HTTP Auth Backup...")

        ServiceManager.getInstance().consumeApi(AUTH_API_BASE, Handler { apiResult ->
            if (apiResult.failed()) {
                logger.error("HTTP Backup unavailable...")

                resultHandler.handle(ServiceException.fail(502, "Service not available..."))
            } else {
                apiManager.performRequestWithCircuitBreaker(AUTH_API_BASE, resultHandler, Handler { authFuture ->
                    val req = apiResult.result().get(AUTH_TOKEN_ENDPOINT, { httpClientResponse ->
                        if (httpClientResponse.statusCode() == 401) {
                            logger.error("UNAUTHORIZED IN HTTP AUTH")

                            authFuture.fail("Unauthorized...")
                        } else {
                            httpClientResponse.bodyHandler({ responseData ->
                                logger.debug("Received: " + responseData.toString() + " from auth.")

                                val jsonObjectBody = responseData.toJsonObject()

                                logger.debug("AUTH FROM HTTP IS: " + Json.encodePrettily(jsonObjectBody))

                                val tokenContainer = Json.decodeValue<TokenContainer>(
                                        jsonObjectBody.getJsonObject("tokenContainer")
                                                .toString(), TokenContainer::class.java)
                                val userProfile = Json.decodeValue<UserProfile>(
                                        jsonObjectBody.getJsonObject("userProfile").toString(), UserProfile::class.java)

                                val authPackage = AuthPackage(tokenContainer, userProfile)

                                authFuture.complete(authPackage)

                                logger.debug("Auth result returned...")
                            })
                        }
                    }).exceptionHandler({ message ->
                        logger.error("HTTP Auth ERROR: $message")

                        authFuture.fail(message)
                    })

                    req.putHeader("Authorization", "Bearer " + token!!)
                    req.putHeader("X-Authorization-Provider", provider)
                    req.setTimeout(5000L)
                    req.end()
                }, { e -> resultHandler.handle(Future.failedFuture(USER_NOT_VERIFIED)) })
            }
        })
    }

    @Fluent
    fun authenticateAndAuthorize(jwt: String?, authorization: Authorization,
                                 resultHandler: Handler<AsyncResult<VerifyResult>>): AuthUtils {
        logger.debug("Auth request ready: " + authorization.toJson().encodePrettily())

        if (jwt == null) {
            logger.error("JWT cannot be null!")

            resultHandler.handle(Future.failedFuture("JWT cannot be null!"))
        } else {
            val backup : (Throwable) -> Unit = {
                httpVerifyBackUp(jwt, authorization, Handler { httpResult ->
                    logger.debug("Received HTTP Verify Result: " + httpResult.succeeded())

                    if (httpResult.failed()) {
                        resultHandler.handle(Future.failedFuture<VerifyResult>(httpResult.cause()))
                    } else {
                        resultHandler.handle(Future.succeededFuture(httpResult.result()))
                    }
                })
            }

            verificationService.compose({ service ->
                logger.debug("Running Eventbus Auth...")

                CircuitBreakerUtils.performRequestWithCircuitBreaker<VerifyResult>(authAuthCircuitBreaker!!, Handler { authRes ->
                    if (authRes.failed()) {
                        resultHandler.handle(ServiceException.fail(500, "Unknown error..."))
                    } else {
                        if (authRes.result() == null) {
                            resultHandler.handle(ServiceException.fail(401, "Not authorized!"))
                        } else {
                            resultHandler.handle(Future.succeededFuture(authRes.result()))
                        }
                    }
                }, Handler {
                    attemptAuthOnEventBus(it, service, jwt, authorization) }, backup)
            }, Future.future<Any>().setHandler { failure -> backup(failure.cause()) })
        }

        return this
    }

    private fun attemptAuthOnEventBus(authFuture: Future<VerifyResult>, verificationService: VerificationService,
                                      jwt: String?, authorization: Authorization) {
        logger.debug("Running Auth on Eventbus, attempt: " + authVerifyCircuitBreaker!!.failureCount())

        verificationService.verifyJWT(jwt!!, authorization, Handler {
            if (authFuture.isComplete) {
                logger.error("Ignoring result, authFuture already completed:" + it.cause())
            } else {
                if (it.failed()) {
                    logger.error("Failed verification service...")

                    if (it.cause() is ServiceException) {
                        ServiceManager.handleResultFailed(it.cause())
                        val se = it.cause() as ServiceException

                        if (se.failureCode() == 401) {
                            authFuture.complete(null)
                        } else {
                            authFuture.fail(it.cause())
                        }
                    } else {
                        logger.error(it.cause())

                        authFuture.fail(it.cause())
                    }
                } else {
                    val verifyResult = it.result()

                    if (verifyResult != null) {
                        logger.debug("Authenticated!")
                        logger.debug(Json.encodePrettily(verifyResult))

                        authFuture.complete(verifyResult)
                    } else {
                        logger.error("Access Denied!")

                        authFuture.fail(it.cause())
                    }
                }
            }
        })
    }

    private fun httpVerifyBackUp(jwt: String?, authorization: Authorization,
                                 resultHandler: Handler<AsyncResult<VerifyResult>>) {
        logger.debug("Running HTTP Verify Backup...")

        ServiceManager.getInstance().consumeApi(AUTH_API_BASE, Handler {
            if (it.failed()) {
                logger.error("HTTP Backup unavailable...")

                resultHandler.handle(ServiceException.fail(502, "Service not available..."))
            } else {
                apiManager.performRequestWithCircuitBreaker(AUTH_API_BASE, resultHandler, Handler { authFuture ->
                    val req = it.result().get(AUTH_VERIFY_ENDPOINT, {
                        if (it.statusCode() == 200) {
                            it.bodyHandler({ bodyResult ->
                                logger.debug("Auth Success!")

                                val bodyAsJson = bodyResult.toJsonObject()

                                logger.debug("Auth body: " + Json.encodePrettily(bodyAsJson))

                                val verifyResult = Json.decodeValue<VerifyResult>(bodyAsJson.encode(), VerifyResult::class.java)

                                authFuture.complete(verifyResult)
                            })
                        } else {
                            authFuture.fail("User not authenticated!")
                        }
                    }).exceptionHandler({ message ->
                        logger.error("HTTP Auth ERROR: $message")

                        authFuture.fail(message)
                    })

                    req.putHeader("Authorization", "Bearer " + jwt!!)
                    req.putHeader("X-Authorization-Type",
                            String(Base64.getEncoder().encode(authorization.toJson().encode().toByteArray())))
                    req.setTimeout(5000L)
                    req.end()
                }, { resultHandler.handle(Future.failedFuture(USER_NOT_VERIFIED)) })
            }
        })
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthUtils::class.java.simpleName)

        private const val AUTH_AUTH_CIRCUTBREAKER_NAME = "com.auth.auth.circuitbreaker"
        private const val AUTH_VERIFY_CIRCUTBREAKER_NAME = "com.auth.verify.circuitbreaker"

        const val USER_IDENTIFIER = "userId"

        private const val USER_NOT_VERIFIED = "NOT_VERIFIED"

        private const val AUTH_API_BASE = "AUTH"

        var instance: AuthUtils? = null
            get() {
                if (field == null) {
                    field = AuthUtils()
                }

                return field
            }
    }
}
