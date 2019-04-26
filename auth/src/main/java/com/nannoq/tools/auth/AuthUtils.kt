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
import io.vertx.serviceproxy.ServiceException.fail
import java.util.Base64

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

    private lateinit var authAuthCircuitBreaker: CircuitBreaker
    private lateinit var authVerifyCircuitBreaker: CircuitBreaker

    private var authCircuitBreakerEvents: MessageConsumer<JsonObject>? = null
    private var verifyCircuitBreakerEvents: MessageConsumer<JsonObject>? = null
    private val apiManager: APIManager

    private val authenticationService: Future<AuthenticationService>
        get() {
            val authenticationServiceFuture = Future.future<AuthenticationService>()

            ServiceManager.getInstance().consumeService(AuthenticationService::class.java, Handler {
                when {
                    it.failed() -> {
                        logger.error("Failed Auth Service fetch...")

                        authenticationServiceFuture.fail(it.cause())
                    }
                    else -> authenticationServiceFuture.complete(it.result())
                }
            })

            return authenticationServiceFuture
        }

    private val verificationService: Future<VerificationService>
        get() {
            val verificationServiceFuture = Future.future<VerificationService>()

            ServiceManager.getInstance().consumeService(VerificationService::class.java, Handler {
                when {
                    it.failed() -> {
                        logger.error("Failed Verification Service fetch...")

                        verificationServiceFuture.fail(it.cause())
                    }
                    else -> verificationServiceFuture.complete(it.result())
                }
            })

            return verificationServiceFuture
        }

    private constructor(appConfig: JsonObject = Vertx.currentContext().config()) :
            this(Vertx.currentContext().owner(), appConfig)

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
                .openHandler { logger.info("$AUTH_AUTH_CIRCUTBREAKER_NAME OPEN") }
                .halfOpenHandler { logger.info("$AUTH_AUTH_CIRCUTBREAKER_NAME HALF-OPEN") }
                .closeHandler { logger.info("$AUTH_AUTH_CIRCUTBREAKER_NAME CLOSED") }
        authAuthCircuitBreaker.close()

        authVerifyCircuitBreaker = CircuitBreaker.create(AUTH_VERIFY_CIRCUTBREAKER_NAME, vertx,
                CircuitBreakerOptions()
                        .setMaxFailures(3)
                        .setTimeout(1000)
                        .setFallbackOnFailure(true)
                        .setResetTimeout(10000)
                        .setNotificationAddress(AUTH_VERIFY_CIRCUTBREAKER_NAME)
                        .setNotificationPeriod(60000L * 60 * 6))
                .openHandler { logger.info("$AUTH_VERIFY_CIRCUTBREAKER_NAME OPEN") }
                .halfOpenHandler { logger.info("$AUTH_AUTH_CIRCUTBREAKER_NAME HALF-OPEN") }
                .closeHandler { logger.info("$AUTH_VERIFY_CIRCUTBREAKER_NAME CLOSED") }
        authVerifyCircuitBreaker.close()
    }

    private fun prepareListeners() {
        authCircuitBreakerEvents = vertx.eventBus().consumer(AUTH_AUTH_CIRCUTBREAKER_NAME) {
            CircuitBreakerUtils.handleCircuitBreakerEvent(authAuthCircuitBreaker, it) }

        verifyCircuitBreakerEvents = vertx.eventBus().consumer(AUTH_VERIFY_CIRCUTBREAKER_NAME) {
            CircuitBreakerUtils.handleCircuitBreakerEvent(authVerifyCircuitBreaker, it) }
    }

    @Fluent
    fun convertExternalToken(token: String?, provider: String, resultHandler: Handler<AsyncResult<AuthPackage>>): AuthUtils {
        logger.debug("Auth request ready for: $provider")

        when (token) {
            null -> {
                logger.error("Token cannot be null!")

                resultHandler.handle(Future.failedFuture("Token cannot be null!"))
            }
            else -> {
                val backup: (Throwable) -> Unit = {
                    httpAuthBackUp(token, provider, Handler {
                        logger.debug("Launching http backup...")

                        when {
                            it.failed() -> resultHandler.handle(Future.failedFuture<AuthPackage>(it.cause()))
                            else -> resultHandler.handle(Future.succeededFuture(it.result()))
                        }
                    })
                }

                authenticationService.compose({ service ->
                    logger.debug("Running Eventbus Auth...")

                    CircuitBreakerUtils.performRequestWithCircuitBreaker(authAuthCircuitBreaker, resultHandler, Handler {
                        attemptAuthConversionOnEventBus(it, service, token, provider) }, backup)
                }, Future.future<Any>().setHandler { failure -> backup(failure.cause()) })
            }
        }

        return this
    }

    private fun attemptAuthConversionOnEventBus(
        authFuture: Future<AuthPackage>,
        authenticationService: AuthenticationService,
        token: String?,
        provider: String
    ) {
        authenticationService.createJwtFromProvider(token!!, provider) {
            when {
                authFuture.isComplete -> logger.error("Ignoring result, authFuture already completed: " + it.cause())
                else ->
                    when {
                        it.failed() -> {
                            logger.error("Conversion failed!")

                            when {
                                it.cause() is ServiceException -> {
                                    ServiceManager.handleResultFailed(it.cause())
                                    authFuture.complete(null)
                                }
                                else -> authFuture.fail(it.cause())
                            }
                        }
                        else -> {
                            logger.debug("Conversion ok, returning result...")

                            authFuture.complete(it.result())
                        }
                    }
            }
        }
    }

    private fun httpAuthBackUp(
        token: String?,
        provider: String,
        resultHandler: Handler<AsyncResult<AuthPackage>>
    ) {
        logger.debug("Running HTTP Auth Backup...")

        ServiceManager.getInstance().consumeApi(AUTH_API_BASE, Handler {
            when {
                it.failed() -> {
                    logger.error("HTTP Backup unavailable...")

                    resultHandler.handle(fail(502, "Service not available..."))
                }
                else -> apiManager.performRequestWithCircuitBreaker(AUTH_API_BASE, resultHandler, Handler { authFuture ->
                    val req = it.result().get(AUTH_TOKEN_ENDPOINT) { response ->
                        when {
                            response.statusCode() == 401 -> {
                                logger.error("UNAUTHORIZED IN HTTP AUTH")

                                authFuture.fail("Unauthorized...")
                            }
                            else -> response.bodyHandler { buffer ->
                                logger.debug("Received: $buffer from auth.")

                                val jsonObjectBody = buffer.toJsonObject()

                                logger.debug("AUTH FROM HTTP IS: " + Json.encodePrettily(jsonObjectBody))

                                val tokenContainer = Json.decodeValue<TokenContainer>(
                                        jsonObjectBody.getJsonObject("tokenContainer")
                                                .toString(), TokenContainer::class.java)
                                val userProfile = Json.decodeValue<UserProfile>(
                                        jsonObjectBody.getJsonObject("userProfile").toString(), UserProfile::class.java)

                                val authPackage = AuthPackage(tokenContainer, userProfile)

                                authFuture.complete(authPackage)

                                logger.debug("Auth result returned...")
                            }
                        }
                    }.exceptionHandler { message ->
                        logger.error("HTTP Auth ERROR: $message")

                        authFuture.fail(message)
                    }

                    req.putHeader("Authorization", "Bearer " + token!!)
                    req.putHeader("X-Authorization-Provider", provider)
                    req.setTimeout(5000L)
                    req.end()
                }) { resultHandler.handle(Future.failedFuture(USER_NOT_VERIFIED)) }
            }
        })
    }

    @Fluent
    fun authenticateAndAuthorize(
        jwt: String?,
        authorization: Authorization,
        resultHandler: Handler<AsyncResult<VerifyResult>>
    ): AuthUtils {
        logger.debug("Auth request ready: " + authorization.toJson().encodePrettily())

        when (jwt) {
            null -> {
                logger.error("JWT cannot be null!")

                resultHandler.handle(Future.failedFuture("JWT cannot be null!"))
            }
            else -> {
                val backup: (Throwable) -> Unit = {
                    httpVerifyBackUp(jwt, authorization, Handler {
                        logger.debug("Received HTTP Verify Result: " + it.succeeded())

                        when {
                            it.failed() -> resultHandler.handle(Future.failedFuture<VerifyResult>(it.cause()))
                            else -> resultHandler.handle(Future.succeededFuture(it.result()))
                        }
                    })
                }

                verificationService.compose({ service ->
                    logger.debug("Running Eventbus Auth...")

                    CircuitBreakerUtils.performRequestWithCircuitBreaker<VerifyResult>(authAuthCircuitBreaker, Handler {
                        when {
                            it.failed() -> resultHandler.handle(fail(500, "Unknown error..."))
                            else ->
                                when {
                                    it.result() == null -> resultHandler.handle(fail(401, "Not authorized!"))
                                    else -> resultHandler.handle(Future.succeededFuture(it.result()))
                                }
                        }
                    }, Handler { attemptAuthOnEventBus(it, service, jwt, authorization) }, backup)
                }, Future.future<Any>().setHandler { failure -> backup(failure.cause()) })
            }
        }

        return this
    }

    private fun attemptAuthOnEventBus(
        authFuture: Future<VerifyResult>,
        verificationService: VerificationService,
        jwt: String?,
        authorization: Authorization
    ) {
        logger.debug("Running Auth on Eventbus, attempt: " + authVerifyCircuitBreaker.failureCount())

        verificationService.verifyJWT(jwt!!, authorization) {
            when {
                authFuture.isComplete -> logger.error("Ignoring result, authFuture already completed:" + it.cause())
                else ->
                    when {
                        it.failed() -> {
                            logger.error("Failed verification service...")

                            when {
                                it.cause() is ServiceException -> {
                                    ServiceManager.handleResultFailed(it.cause())
                                    val se = it.cause() as ServiceException

                                    when {
                                        se.failureCode() == 401 -> authFuture.complete(null)
                                        else -> authFuture.fail(it.cause())
                                    }
                                }
                                else -> {
                                    logger.error(it.cause())

                                    authFuture.fail(it.cause())
                                }
                            }
                        }
                        else -> {
                            val verifyResult = it.result()

                            when {
                                verifyResult != null -> {
                                    logger.debug("Authenticated!")
                                    logger.debug(Json.encodePrettily(verifyResult))

                                    authFuture.complete(verifyResult)
                                }
                                else -> {
                                    logger.error("Access Denied!")

                                    authFuture.fail(it.cause())
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun httpVerifyBackUp(
        jwt: String?,
        authorization: Authorization,
        resultHandler: Handler<AsyncResult<VerifyResult>>
    ) {
        logger.debug("Running HTTP Verify Backup...")

        ServiceManager.getInstance().consumeApi(AUTH_API_BASE, Handler {
            when {
                it.failed() -> {
                    logger.error("HTTP Backup unavailable...")

                    resultHandler.handle(fail(502, "Service not available..."))
                }
                else -> apiManager.performRequestWithCircuitBreaker(AUTH_API_BASE, resultHandler, Handler { authFuture ->
                    val req = it.result().get(AUTH_VERIFY_ENDPOINT) { response ->
                        when {
                            response.statusCode() == 200 -> response.bodyHandler { buffer ->
                                logger.debug("Auth Success!")

                                val bodyAsJson = buffer.toJsonObject()

                                logger.debug("Auth body: " + Json.encodePrettily(bodyAsJson))

                                val verifyResult = Json.decodeValue<VerifyResult>(
                                        bodyAsJson.encode(), VerifyResult::class.java)

                                authFuture.complete(verifyResult)
                            }
                            else -> authFuture.fail("User not authenticated!")
                        }
                    }.exceptionHandler { throwable ->
                        logger.error("HTTP Auth ERROR: $throwable")

                        authFuture.fail(throwable)
                    }

                    req.putHeader("Authorization", "Bearer " + jwt!!)
                    req.putHeader("X-Authorization-Type",
                            String(Base64.getEncoder().encode(authorization.toJson().encode().toByteArray())))
                    req.setTimeout(5000L)
                    req.end()
                }) { resultHandler.handle(Future.failedFuture(USER_NOT_VERIFIED)) }
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
