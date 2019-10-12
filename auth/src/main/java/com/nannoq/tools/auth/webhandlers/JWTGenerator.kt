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

import com.nannoq.tools.auth.models.AuthPackage
import com.nannoq.tools.auth.models.TokenContainer
import com.nannoq.tools.auth.services.AuthenticationService
import com.nannoq.tools.auth.services.AuthenticationServiceImpl.Companion.FACEBOOK
import com.nannoq.tools.auth.services.AuthenticationServiceImpl.Companion.GOOGLE
import com.nannoq.tools.auth.services.AuthenticationServiceImpl.Companion.INSTAGRAM
import com.nannoq.tools.auth.utils.AuthFutures.authFail
import com.nannoq.tools.auth.utils.AuthFutures.authFailRedirect
import com.nannoq.tools.auth.utils.AuthFutures.denyRequest
import com.nannoq.tools.auth.utils.AuthFutures.getToken
import com.nannoq.tools.auth.utils.AuthPackageHandler
import com.nannoq.tools.cluster.apis.APIManager
import com.nannoq.tools.cluster.services.ServiceManager
import com.nannoq.tools.repository.models.ModelUtils
import com.nannoq.tools.repository.repository.redis.RedisUtils
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.REQUEST_LOG_TAG
import com.nannoq.tools.web.requestHandlers.RequestLogHandler.Companion.addLogMessageToRequestLog
import com.nannoq.tools.web.responsehandlers.ResponseLogHandler.Companion.BODY_CONTENT_TAG
import facebook4j.FacebookException
import facebook4j.FacebookFactory
import facebook4j.conf.ConfigurationBuilder
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import io.vertx.redis.RedisClient
import java.security.NoSuchAlgorithmException
import java.util.function.Consumer
import org.jinstagram.auth.InstagramAuthService
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

/**
 * This class defines a Handler implementation that receives all traffic for endpoints that handle JWT generator, e.g.
 * authentication.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
@Suppress("unused")
open class JWTGenerator(
    protected val vertx: Vertx,
    private val appConfig: JsonObject,
    private val authenticator: AuthenticationService,
    private val authPackageHandler: AuthPackageHandler,
    private val domainIdentifier: String?
) : Handler<RoutingContext> {
    private val logger = LoggerFactory.getLogger(JWTGenerator::class.java.simpleName)

    private val CMS_ROOT: String
    private val GOOGLE_AUTH_URL: String
    private val redisClient: RedisClient = RedisUtils.getRedisClient(vertx, appConfig)
    private val callbackUrl: String
    private val EMAIL_HASH_KEY_BASE: String
    private var userIdFunction: (AuthPackage) -> String

    init {
        val googleClientId = appConfig.getString("googleClientId")
        val CALL_BACK_PROVIDER_URL = appConfig.getString("callbackProviderUrl")
        this.callbackUrl = appConfig.getString("callBackRoot") + CALL_BACK_PROVIDER_URL

        CMS_ROOT = appConfig.getString("callBackRoot")
        GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "scope=openid%20email%20profile&" +
                "state=:stateToken&" +
                "redirect_uri=" + CMS_ROOT + "/auth/api/oauth2/auth/google&" +
                "response_type=code&" +
                "client_id=" + googleClientId + "&" +
                "prompt=consent&" +
                "include_granted_scopes=true&" +
                "access_type=offline"

        this.EMAIL_HASH_KEY_BASE = appConfig.getString("emailHashKeybase")

        userIdFunction = {
            try {
                ModelUtils.hashString(it.userProfile.email + EMAIL_HASH_KEY_BASE)
            } catch (e: NoSuchAlgorithmException) {
                logger.error("No Algorithm Available!", e)

                it.userProfile.email
            }
        }
    }

    @Fluent
    fun withUserIdGenerator(userIdGenerator: (AuthPackage) -> String): AuthenticationService {
        return setUserIdFunction(userIdGenerator)
    }

    @Fluent
    fun setUserIdFunction(userIdFunction: (AuthPackage) -> String): AuthenticationService {
        this.userIdFunction = userIdFunction

        return authenticator
    }

    override fun handle(routingContext: RoutingContext) {
        val request = routingContext.request()

        val authToken = request.getParam("code")
        val authProvider = request.getParam("provider")

        if (authProvider != null && authToken != null) {
            when (authProvider.toUpperCase()) {
                INSTAGRAM -> handleAccessToken(authProvider, "Bearer $authToken", routingContext)
                FACEBOOK -> handleFacebookAuth(routingContext, authToken, authProvider)
                GOOGLE -> handleGoogleAuth(routingContext, authToken, authProvider)
                else -> {
                    unAuthorized(routingContext)

                    logger.error("Unknown auth provider for Auth Flow...")
                }
            }
        } else {
            unAuthorized(routingContext)

            logger.error("Unknown request...")
        }
    }

    private fun handleGoogleAuth(routingContext: RoutingContext, authToken: String, authProvider: String) {
        logger.info("Authing for google...")

        val resultHandler = Handler<AsyncResult<String>> {
            when {
                it.succeeded() -> if (it.result() != null) {
                    logger.info("Completed Google Auth...")

                    handleAccessToken(authProvider, "Bearer " + it.result(), routingContext)
                } else {
                    logger.error("Failed Google Auth...", it.cause())

                    unAuthorized(routingContext)
                }
                else -> {
                    logger.error("Failed Google Auth...", it.cause())

                    unAuthorized(routingContext)
                }
            }
        }

        val opts = HttpClientOptions().setSsl(true)
        val req = vertx.createHttpClient(opts)
                .post(443, "www.googleapis.com", "/oauth2/v4/token")
                .putHeader("Content-Type", "application/x-www-form-urlencoded")

        val authHandler = Handler<Promise<String>> {
            req.handler { response ->
                when {
                    response.statusCode() in 200..399 -> {
                        logger.info("Google Status is: " + response.statusCode())

                        response.bodyHandler { body ->
                            val res = body.toJsonObject()

                            it.complete(res.getString("id_token"))
                        }
                    }
                    else -> {
                        logger.error(response.statusCode())
                        logger.error(response.statusMessage())
                        logger.error(response.bodyHandler { body ->
                            logger.error("UNAUTHORIZED!")

                            logger.error(Json.encodePrettily(body.toJsonObject()))

                            it.fail(UnknownError(response.statusMessage()))
                        })
                    }
                }
            }.end("code=" + authToken + "&" +
                    "client_id=" + appConfig.getString("googleClientId") + "&" +
                    "client_secret=" + appConfig.getString("googleClientSecret") + "&" +
                    "redirect_uri=" + callbackUrl.replace(":provider", "google") +
                    "&" + "grant_type=authorization_code")
        }

        APIManager.performRequestWithCircuitBreaker(resultHandler, authHandler) {
            logger.error("Failed Google Auth...")

            unAuthorized(routingContext)
        }
    }

    private fun handleFacebookAuth(routingContext: RoutingContext, authToken: String, authProvider: String) {
        val appId = vertx.orCreateContext.config().getString("faceBookAppId")
        val appSecret = vertx.orCreateContext.config().getString("faceBookAppSecret")

        vertx.executeBlocking<Any>({
            val cb = ConfigurationBuilder()
            cb.setAppSecretProofEnabled(true)
            cb.setOAuthAppId(appId)
            cb.setOAuthAppSecret(appSecret)
            val facebook = FacebookFactory(cb.build()).instance
            facebook.oAuthCallbackURL = callbackUrl.replace(":provider", "facebook")

            logger.info("Authing for facebook...")

            try {
                val token = facebook.getOAuthAccessToken(authToken)

                logger.info("Token is: " + token.token)

                handleAccessToken(authProvider, "Bearer " + token.token, routingContext)
            } catch (e: FacebookException) {
                logger.error("Failed Facebook Operation", e)

                unAuthorized(routingContext)
            }
        }, false, {})
    }

    fun directAuth(routingContext: RoutingContext) {
        val request = routingContext.request()

        val sb = routingContext.get<StringBuffer>(REQUEST_LOG_TAG)
        val authToken = request.getHeader("Authorization")
        val authProvider = request.getHeader("X-Authorization-Provider")

        when {
            authToken != null && authProvider != null && authToken.startsWith("Bearer") -> {
                val token = Jsoup.clean(authToken, Whitelist.none()).substring("Bearer".length).trim { it <= ' ' }
                val upperedAuthProvider = Jsoup.clean(authProvider, Whitelist.none()).toUpperCase()

                authenticator.createJwtFromProvider(token, upperedAuthProvider) {
                    when {
                        it.failed() -> {
                            logger.error("AUTH Failed: $sb", it.cause())

                            routingContext.response().statusCode = 401
                            routingContext.next()
                        }
                        else -> {
                            val authPackage = it.result()

                            try {
                                val userId = userIdFunction(authPackage)

                                authPackageHandler.processDirectAuth(authPackage, userId, Handler { result ->
                                    when {
                                        result.failed() -> {
                                            logger.error("Failed processing Direct Auth!",
                                                    result.cause())

                                            routingContext.response().statusCode = 422
                                            routingContext.next()
                                        }
                                        else -> {
                                            routingContext.response().statusCode = 200
                                            routingContext.put(BODY_CONTENT_TAG, result.result().encode())
                                            routingContext.next()
                                        }
                                    }
                                })
                            } catch (e: Exception) {
                                logger.error("AUTH Failed: $sb", e)

                                routingContext.response().statusCode = 500
                                routingContext.next()
                            }
                        }
                    }
                }
            }
            else -> {
                logger.error("Invalid parameters!")

                val errorObject = JsonObject()
                if (authToken == null) errorObject.put("header_error", "Authorization Header cannot be null!")
                if (authProvider == null) errorObject.put("header_error", "X-Authorization-Provider Header cannot be null!")
                if (domainIdentifier == null) errorObject.put("path_error", "FeedId cannot be null!")

                routingContext.put(BODY_CONTENT_TAG, errorObject.encodePrettily())
                routingContext.response().statusCode = 401
                routingContext.next()
            }
        }
    }

    private fun handleAccessToken(authProvider: String, authToken: String, routingContext: RoutingContext) {
        getReceivedUserState(routingContext).future().compose({
            getLocation(it).future().compose({ location ->
                handleToken(authToken, it, location, authProvider).future().compose({ authPackage ->
                    finalizeResponse(location, it, authPackage, routingContext) },
                        authFailRedirect<Any>(routingContext)) },
                    authFailRedirect<Any>(routingContext))
        }, authFailRedirect<Any>(routingContext))
    }

    private fun handleToken(authToken: String?, state: String, location: String?, authProvider: String?): Promise<AuthPackage> {
        val authFuture = Promise.promise<AuthPackage>()

        when {
            authToken != null && authProvider != null && location != null ->
                when {
                    authToken.startsWith("Bearer ") -> {
                        val token = authToken.substring("Bearer".length).trim { it <= ' ' }
                        authenticator.createJwtFromProvider(token, authProvider.toUpperCase()) { result ->
                            when {
                                result.failed() -> {
                                    ServiceManager.handleResultFailed(result.cause())

                                    authFuture.fail("$CMS_ROOT#code=401&error=Unauthorized")
                                }
                                else -> {
                                    val authPackage = result.result()
                                    logger.info("Result is: " + Json.encodePrettily(authPackage))

                                    purgeState(authProvider, state)

                                    authFuture.complete(authPackage)
                                }
                            }
                        }
                    }
                    else -> authFuture.fail("$CMS_ROOT#code=400&error=Invalid Auth headers")
                }
            else -> authFuture.fail("$CMS_ROOT#code=401&error=Unauthorized")
        }

        return authFuture
    }

    private fun purgeState(authProvider: String, state: String) {
        RedisUtils.performJedisWithRetry(redisClient) { it.del(state) { result ->
            logger.info("Deleted state for " + state + " is " + result.result()) }
        }

        if (authProvider.toUpperCase() == INSTAGRAM) {
            RedisUtils.performJedisWithRetry(redisClient) {
                it.del(state + "_forUser") { result ->
                    logger.info("Deleted state_forUser for " + state + " is " + result.result())
                }
            }
        }
    }

    private fun getLocation(state: String): Promise<String> {
        val stateFuture = Promise.promise<String>()

        RedisUtils.performJedisWithRetry(redisClient) { intRedis ->
            intRedis.get(state) {
                when {
                    it.failed() ->
                        stateFuture.fail(InternalError("$CMS_ROOT#code=422&error=Unable to verify user state..."))
                    else -> stateFuture.complete(it.result())
                }
            }
        }

        return stateFuture
    }

    private fun getReceivedUserState(routingContext: RoutingContext): Promise<String> {
        val stateFuture = Promise.promise<String>()
        val stateParam = routingContext.request().getParam("state")

        when {
            stateParam != null -> stateFuture.complete(stateParam)
            else -> stateFuture.fail(
                    IllegalArgumentException("$CMS_ROOT#code=400&error=State cannot be null from external"))
        }

        return stateFuture
    }

    private fun finalizeResponse(url: String, state: String, authPackage: AuthPackage, routingContext: RoutingContext) {
        logger.info("Building url for redirect...")

        val finalUrl = url + "#state=" +
                state + "&jwt=" + authPackage.tokenContainer.accessToken +
                "&refresh_token=" + authPackage.tokenContainer.refreshToken +
                "&id=" + authPackage.userProfile.userId

        logger.debug("Url is: $finalUrl")

        val userId = userIdFunction(authPackage)

        authPackageHandler.processOAuthFlow(authPackage, userId, finalUrl, Handler {
            when {
                it.failed() -> routingContext.response()
                        .setStatusCode(302)
                        .putHeader(HttpHeaders.LOCATION, "#code=500&error=UNKNOWN")
                        .end()
                else -> {
                    val res = it.result()
                    val location = res.getString("Location")

                    routingContext.response()
                            .setStatusCode(302)
                            .putHeader(HttpHeaders.LOCATION, location)
                            .end()
                }
            }
        })
    }

    fun returnAuthUrl(routingContext: RoutingContext) {
        val success = Consumer<String> {
            routingContext.response()
                    .setStatusCode(302)
                    .putHeader(HttpHeaders.LOCATION, it)
                    .end()
        }

        getProvider(routingContext).future().compose({ provider ->
            getUserState(routingContext).future().compose({ state ->
                getLocation(routingContext, state).future().compose({ location ->
                    setState(state, location).future().compose({
                        constructAuthUrl(routingContext, state, location, provider).future().compose(Handler {
                            success.accept(it)
                        }, authFailRedirect<Any>(routingContext))
                    }, authFailRedirect<Any>(routingContext))
                }, authFailRedirect<Any>(routingContext))
            }, denyRequest<Any>(routingContext))
        }, denyRequest<Any>(routingContext))
    }

    private fun constructAuthUrl(
        routingContext: RoutingContext,
        state: String,
        location: String,
        provider: String
    ): Promise<String> {
        val locationFuture = Promise.promise<String>()

        when (provider.toUpperCase()) {
            INSTAGRAM -> {
                val forUserReference = routingContext.request().getParam("forUser")

                when {
                    forUserReference != null -> getInstagramUrl(state, forUserReference, location, Handler {
                        when {
                            it.failed() -> locationFuture.fail(InternalError(it.cause().message))
                            else -> locationFuture.complete(it.result())
                        }
                    })
                    else -> locationFuture.fail(SecurityException(location +
                            "#code=400&error=" + "InstaGram does not support emails from API, " +
                            "and can only be federated into an established user. " +
                            "Please add id as email of the user to federate into as a " +
                            "query param with name \"forUser\"."))
                }
            }
            FACEBOOK -> vertx.executeBlocking<String>({
                val config = vertx.orCreateContext.config()
                val appId = config.getString("faceBookAppId")
                val appSecret = config.getString("faceBookAppSecret")

                val facebook = FacebookFactory().instance
                facebook.setOAuthAppId(appId, appSecret)
                facebook.setOAuthPermissions("public_profile,email,user_friends")

                it.complete(facebook.getOAuthAuthorizationURL(
                        callbackUrl.replace(":provider", "facebook"), state))
            }, false) {
                val url = it.result()

                when {
                    url != null && url.isNotEmpty() -> locationFuture.complete(it.result())
                    else -> locationFuture.fail(InternalError("$location#code=500&error=Unknown"))
                }
            }
            GOOGLE -> {
                val authUrl = GOOGLE_AUTH_URL.replace(":stateToken", state)
                locationFuture.complete(authUrl)
            }
            else -> locationFuture.fail("$location#code=400&error=Unknown")
        }

        return locationFuture
    }

    private fun getInstagramUrl(
        state: String,
        userRef: String,
        location: String,
        resultHandler: Handler<AsyncResult<String>>
    ) {
        val finalState = state + "_forUser"

        RedisUtils.performJedisWithRetry(redisClient) { internalRedis ->
            internalRedis.set(finalState, userRef) { res ->
                when {
                    res.failed() -> {
                        logger.error("Cannot set forUser, aborting instagram...", res.cause())

                        RedisUtils.performJedisWithRetry(redisClient) { intRedis ->
                            intRedis.del(state) {
                                logger.info("Deleted state for " + state + " is " + it.result())
                            }
                        }

                        resultHandler.handle(Future.failedFuture(
                                "$location#code=500&error=Internal Server Error, Retry."))
                    }
                    else -> {
                        val clientId = appConfig.getString("instaClientId")
                        val clientSecret = appConfig.getString("instaClientSecret")

                        val instagram = InstagramAuthService()
                                .apiKey(clientId)
                                .apiSecret(clientSecret)
                                .callback(callbackUrl.replace(":provider", "instagram"))
                                .scope("basic public_content follower_list likes comments relationships")
                                .build()

                        resultHandler.handle(Future.succeededFuture(
                                instagram.authorizationUrl + "&state=" + state))
                    }
                }
            }
        }
    }

    private fun setState(state: String, location: String): Promise<Void> {
        val voidFuture = Promise.promise<Void>()

        RedisUtils.performJedisWithRetry(redisClient) {
            it.set(state, location) { result ->
                when {
                    result.failed() -> voidFuture.fail(
                            InternalError("$location#code=500&error=Internal Server Error, Retry."))
                    else -> voidFuture.complete()
                }
            }
        }

        return voidFuture
    }

    private fun getLocation(routingContext: RoutingContext, state: String?): Promise<String> {
        val locationFuture = Promise.promise<String>()

        var location: String? = routingContext.request().getParam("location")
        if (location == null) location = CMS_ROOT

        when {
            state == null || state.length < 30 ->
                locationFuture.fail(IllegalArgumentException(location + "#code=400&error=" +
                        "Must have a state query param, containing a random or " +
                        "pseudo-random string of at least 30 characters."))
            else -> locationFuture.complete(location)
        }

        return locationFuture
    }

    private fun getProvider(routingContext: RoutingContext): Promise<String> {
        val providerFuture = Promise.promise<String>()

        when (val provider = routingContext.request().getParam("provider")) {
            null -> providerFuture.fail(IllegalArgumentException())
            else -> providerFuture.complete(provider)
        }

        return providerFuture
    }

    private fun getUserState(routingContext: RoutingContext): Promise<String> {
        val stateFuture = Promise.promise<String>()
        val stateParam = routingContext.request().getParam("state")

        when {
            stateParam != null -> stateFuture.complete(stateParam)
            else -> stateFuture.fail(IllegalArgumentException("State cannot be null..."))
        }

        return stateFuture
    }

    fun refreshFromHttp(routingContext: RoutingContext) {
        val success = Consumer<TokenContainer> {
            routingContext.response().statusCode = 200
            routingContext.put(BODY_CONTENT_TAG, it)
            routingContext.next()
        }

        getToken(routingContext).compose({ refreshToken ->
            refreshToken(refreshToken).future().compose(Handler {
                success.accept(it)
            }, authFail<Any>(routingContext))
        }, authFail<Any>(routingContext))
    }

    private fun refreshToken(refreshToken: String): Promise<TokenContainer> {
        val tokenContainerFuture = Promise.promise<TokenContainer>()

        authenticator.refresh(refreshToken) {
            when {
                it.failed() -> tokenContainerFuture.fail(RuntimeException("Unable to refresh for: $refreshToken"))
                else -> tokenContainerFuture.complete(it.result())
            }
        }

        return tokenContainerFuture
    }

    private fun unAuthorized(routingContext: RoutingContext) {
        addLogMessageToRequestLog(routingContext, "Unauthorized!")

        routingContext.response().setStatusCode(302).putHeader("Location", "$CMS_ROOT#code=401&error=Unauthorized").end()
    }
}
