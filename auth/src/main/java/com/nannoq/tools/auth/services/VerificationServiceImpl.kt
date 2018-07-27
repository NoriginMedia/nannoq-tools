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

package com.nannoq.tools.auth.services

import com.nannoq.tools.auth.AuthGlobals.VALIDATION_REQUEST
import com.nannoq.tools.auth.AuthGlobals.VALID_JWT_REGISTRY_KEY
import com.nannoq.tools.auth.models.VerifyResult
import com.nannoq.tools.auth.services.AuthenticationServiceImpl.Companion.REFRESH_TOKEN_SPLITTER
import com.nannoq.tools.auth.utils.Authorization
import com.nannoq.tools.auth.utils.Authorizer
import com.nannoq.tools.repository.repository.redis.RedisUtils
import io.jsonwebtoken.*
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.RedisClient
import io.vertx.serviceproxy.ServiceException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.function.Supplier
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter

/**
 * This class defines an implementation of a VerificationService. It verifies both incoming JWTS, and also checks
 * whether a token has been revoked or not.
 *
 * Accepts HTTP and Eventbus.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class VerificationServiceImpl @Throws(InvalidKeyException::class, NoSuchAlgorithmException::class)
@JvmOverloads constructor(private val vertx: Vertx, appConfig: JsonObject, KEY_BASE: String,
                          private val authorizer: Authorizer,
                          private val userIdsSupplier: Supplier<Future<List<String>>>,
                          private val dev: Boolean = false) : VerificationService {

    private val ISSUER: String
    private val AUDIENCE: String
    private val SIGNING_KEY: SecretKey
    private val domainIdentifier: String
    private val redisClient: RedisClient

    init {
        this.SIGNING_KEY = SecretKeySpec(DatatypeConverter.parseHexBinary(KEY_BASE), KEY_ALGORITHM)
        this.domainIdentifier = appConfig.getString("domainIdentifier")
        this.redisClient = RedisUtils.getRedisClient(vertx, appConfig)
        this.ISSUER = appConfig.getString("authJWTIssuer")
        this.AUDIENCE = appConfig.getString("authJWTAudience")

        initializeKey(KEY_ALGORITHM)
    }

    @Fluent
    private fun setKeyAlgorithm(keyAlgorithm: String): VerificationServiceImpl {
        KEY_ALGORITHM = keyAlgorithm

        return this
    }

    @Fluent
    fun withKeyAlgorithm(keyAlgorithm: String): VerificationServiceImpl {
        return setKeyAlgorithm(keyAlgorithm)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    private fun initializeKey(keyAlgorithm: String) {
        val mac = Mac.getInstance(keyAlgorithm)
        mac.init(SIGNING_KEY)
    }

    @Fluent
    override fun verifyJWT(token: String, authorization: Authorization,
                           resultHandler: Handler<AsyncResult<VerifyResult>>): VerificationService {
        verifyToken(token, Handler {
            if (it.failed()) {
                logger.error("ERROR JWT: " + it.cause())

                resultHandler.handle(ServiceException.fail(
                        401, "ERROR: " + it.cause()))
            } else {
                val claims = it.result()

                try {
                    if (authorization.validate()) {
                        if (authorization.domainIdentifier == VALIDATION_REQUEST) {
                            returnAuth(claims, resultHandler)
                        } else {
                            if (authorizer.isAsync) {
                                verifyAuthorization(claims, authorization, Handler {
                                    authResult(claims, resultHandler, it.succeeded())
                                })
                            } else {
                                authResult(claims, resultHandler, verifyAuthorization(claims, authorization))
                            }
                        }
                    } else {
                        logger.error("Invalid Authorization Field: " + Json.encodePrettily(authorization))

                        notAuthorized(resultHandler)
                    }
                } catch (e: IllegalAccessException) {
                    resultHandler.handle(ServiceException.fail(
                            401, "Not authorized for this resource, invalid AuthTypeToken!"))
                }
            }
        })

        return this
    }

    private fun authResult(claims: Jws<Claims>, resultHandler: Handler<AsyncResult<VerifyResult>>, succeeded: Boolean) {
        if (succeeded) {
            returnAuth(claims, resultHandler)
        } else {
            notAuthorized(resultHandler)
        }
    }

    private fun notAuthorized(resultHandler: Handler<AsyncResult<VerifyResult>>) {
        resultHandler.handle(ServiceException.fail(401, "Not authorized for this resource!"))
    }

    private fun returnAuth(claims: Jws<Claims>, resultHandler: Handler<AsyncResult<VerifyResult>>) {
        val vr = VerifyResult(claims.body.subject)

        logger.debug("User Auth: " + Json.encodePrettily(vr))

        resultHandler.handle(Future.succeededFuture(vr))
    }

    @Fluent
    override fun verifyToken(token: String, resultHandler: Handler<AsyncResult<Jws<Claims>>>): VerificationServiceImpl {
        vertx.executeBlocking<Any>({ verificationFuture ->
            try {
                logger.debug("Verifying Token...")

                val claims = Jwts.parser()
                        .setSigningKey(SIGNING_KEY)
                        .requireIssuer(ISSUER)
                        .requireAudience(AUDIENCE)
                        .parseClaimsJws(token)

                logger.debug("Token parsed...")

                val userId = claims.body.subject
                val id = claims.body.id
                val registry = userId + VALID_JWT_REGISTRY_KEY

                if (dev) {
                    logger.info("DEV ACCEPT")

                    resultHandler.handle(Future.succeededFuture(claims))
                    verificationFuture.complete(Future.succeededFuture<Any>())
                } else {
                    RedisUtils.performJedisWithRetry(redisClient) { intRedis ->
                        intRedis.hget(registry, id) { jwts ->
                            if (jwts.failed()) {
                                resultHandler.handle(ServiceException.fail(
                                        500, "Redis failure..."))
                                verificationFuture.fail(jwts.cause())

                                logger.error("Failed to fetch from redis store...")
                            } else {
                                val result = jwts.result()

                                if (result != null && result.length > 4 && result.contains("____")) {
                                    resultHandler.handle(Future.succeededFuture(claims))
                                    verificationFuture.complete(Future.succeededFuture<Any>())
                                } else {
                                    failedVerify(verificationFuture, resultHandler, jwts, userId, id)
                                }
                            }
                        }
                    }
                }
            } catch (e: MissingClaimException) {
                resultHandler.handle(ServiceException.fail(
                        500, "Unknown error: " + e.message))

                verificationFuture.fail(e)
            } catch (e: IncorrectClaimException) {
                resultHandler.handle(ServiceException.fail(500, "Unknown error: " + e.message))
                verificationFuture.fail(e)
            } catch (e: SignatureException) {
                resultHandler.handle(ServiceException.fail(500, "Unknown error: " + e.message))
                verificationFuture.fail(e)
            } catch (e: ExpiredJwtException) {
                resultHandler.handle(ServiceException.fail(500, "Unknown error: " + e.message))
                verificationFuture.fail(e)
            } catch (e: MalformedJwtException) {
                resultHandler.handle(ServiceException.fail(500, "Unknown error: " + e.message))
                verificationFuture.fail(e)
            } catch (e: PrematureJwtException) {
                resultHandler.handle(ServiceException.fail(500, "Unknown error: " + e.message))
                verificationFuture.fail(e)
            } catch (e: UnsupportedJwtException) {
                resultHandler.handle(ServiceException.fail(500, "Unknown error: " + e.message))
                verificationFuture.fail(e)
            }
        }, false) { result ->
            logger.debug("Result: " + result.succeeded())

            if (result.failed()) {
                logger.error("Verification failed!", result.cause())
            }
        }

        return this
    }

    private fun failedVerify(verificationFuture: Future<Any>,
                             resultHandler: Handler<AsyncResult<Jws<Claims>>>,
                             jwts: AsyncResult<String>, userId: String, id: String) {
        resultHandler.handle(ServiceException.fail(
                401, "Invalid JWT..."))
        verificationFuture.fail(jwts.cause())

        logger.error("Could not validate JWT! user: $userId, id: $id")
    }

    @Throws(IllegalAccessException::class)
    fun verifyAuthorization(claims: Jws<Claims>, authorization: Authorization): Boolean {
        return authorizer.authorize(claims, domainIdentifier, authorization)
    }

    @Fluent
    @Throws(IllegalAccessException::class)
    override fun verifyAuthorization(claims: Jws<Claims>, authorization: Authorization,
                                     resultHandler: Handler<AsyncResult<Boolean>>): VerificationServiceImpl {
        if (authorizer.isAsync) {
            authorizer.authorize(claims, domainIdentifier, authorization, resultHandler)
        } else {
            if (authorizer.authorize(claims, domainIdentifier, authorization)) {
                resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
            } else {
                resultHandler.handle(Future.failedFuture(SecurityException("You are not authorized!")))
            }
        }

        return this
    }

    @Fluent
    override fun revokeToken(token: String,
                             resultHandler: Handler<AsyncResult<Boolean>>): VerificationService {
        verifyToken(token, Handler {
            if (it.failed()) {
                logger.error("Could not verify JWT for revoke...", it.cause())

                resultHandler.handle(ServiceException.fail(401, "Could not verify JWT..."))
            } else {
                val claims = it.result()
                val userId = claims.body.subject
                val id = claims.body.id
                val registry = userId + VALID_JWT_REGISTRY_KEY

                doGarbageCollectionAfterRevoke(registry, id, resultHandler)
            }
        })

        return this
    }

    @Fluent
    override fun revokeUser(userId: String,
                            resultHandler: Handler<AsyncResult<Boolean>>): VerificationService {
        val registry = userId + VALID_JWT_REGISTRY_KEY

        purgeJWTsOnUser(registry, resultHandler)

        return this
    }

    private fun doGarbageCollectionAfterRevoke(registry: String, id: String,
                                               resultHandler: Handler<AsyncResult<Boolean>>) {
        RedisUtils.performJedisWithRetry(redisClient) { intRedis ->
            val transaction = intRedis.transaction()

            transaction.multi { multiResult ->
                transaction.hget(registry, id) { tokenResult ->
                    if (tokenResult.failed()) {
                        logger.error("Unable to delete refreshtoken for revoked JWT!")
                    } else {
                        val refreshArray = tokenResult.result()
                                .split(REFRESH_TOKEN_SPLITTER.toRegex())
                                .dropLastWhile({ it.isEmpty() })
                                .toTypedArray()

                        transaction.del(refreshArray[0]) { delResult ->
                            if (delResult.failed()) {
                                logger.error("Del RefreshToken failed!", delResult.cause())
                            }
                        }

                        transaction.hdel(registry, id) { delJwtValidityResult ->
                            if (delJwtValidityResult.failed()) {
                                logger.error("Del JwtValidity failed!", delJwtValidityResult.cause())
                            }
                        }
                    }
                }
            }

            transaction.exec { execResult ->
                if (execResult.failed()) {
                    logger.error("Failed Destroy transaction!", execResult.cause())

                    resultHandler.handle(ServiceException.fail(
                            500, "Failed revoking old token..."))
                } else {
                    resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                }
            }
        }
    }

    private fun purgeJWTsOnUser(registry: String, resultHandler: Handler<AsyncResult<Boolean>>) {
        RedisUtils.performJedisWithRetry(redisClient) { intRedis ->
            val transaction = intRedis.transaction()

            transaction.multi { multiResult ->
                transaction.hgetall(registry) { tokenResult ->
                    if (tokenResult.failed()) {
                        logger.error("Unable to delete refreshtoken for revoked JWT!")
                    } else {
                        if (tokenResult.result() == null) {
                            logger.debug("Token List is empty!")
                        } else {
                            try {
                                val content = tokenResult.result()

                                if (content.toString().equals("{}", ignoreCase = true)) {
                                    logger.debug("Empty object!")
                                } else {
                                    val arrayOfStrings = JsonObject(content.toString())

                                    arrayOfStrings.forEach { stringObjectEntry ->
                                        val key = stringObjectEntry.key
                                        val value = stringObjectEntry.value
                                        val refreshArray = value.toString().split(REFRESH_TOKEN_SPLITTER.toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()

                                        transaction.del(refreshArray[0]) { delResult ->
                                            if (delResult.failed()) {
                                                logger.error("Failed invalidating: $value", delResult.cause())
                                            }
                                        }

                                        transaction.hdel(registry, key) { delResult ->
                                            if (delResult.failed()) {
                                                logger.error("Failed invalidating jwt: $key", delResult.cause())
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.error("Error performing revocation!", e)
                            }

                        }
                    }
                }
            }

            transaction.exec { execResult ->
                if (execResult.failed()) {
                    logger.error("Failed Destroy transaction!", execResult.cause())

                    resultHandler.handle(ServiceException.fail(
                            500, "Failed revoking all tokens..."))
                } else {
                    resultHandler.handle(Future.succeededFuture(java.lang.Boolean.TRUE))
                }
            }
        }
    }

    @Fluent
    override fun verifyJWTValidity(resultHandler: Handler<AsyncResult<Boolean>>): VerificationService {
        fetchUserIds(Handler {
            if (it.failed()) {
                logger.error("Could not read userids...", it.cause())
            } else {
                it.result().forEach({ id ->
                    RedisUtils.performJedisWithRetry(redisClient) { intRedis ->
                        val registryKey = id + VALID_JWT_REGISTRY_KEY

                        intRedis.hgetall(registryKey) { jwts ->
                            if (jwts.failed()) {
                                logger.error("Could not read jwts for $id", jwts.cause())
                            } else {
                                checkJwts(jwts.result(), id, registryKey, intRedis)
                            }
                        }
                    }
                })
            }
        })

        return this
    }

    private fun fetchUserIds(resultHandler: Handler<AsyncResult<List<String>>>) {
        userIdsSupplier.get().setHandler { idsRes ->
            if (idsRes.failed()) {
                resultHandler.handle(Future.failedFuture(idsRes.cause()))
            } else {
                resultHandler.handle(Future.succeededFuture(idsRes.result()))
            }
        }
    }

    private fun checkJwts(jwtsObject: JsonObject?, userId: String, registryKey: String, intRedis: RedisClient) {
        if (jwtsObject != null) {
            jwtsObject.forEach { stringObjectEntry ->
                val key = stringObjectEntry.key
                val value = stringObjectEntry.value
                val refreshArray = value.toString().split(REFRESH_TOKEN_SPLITTER.toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()

                val date = java.lang.Long.parseLong(refreshArray[1])
                val tokenTime = Calendar.getInstance()
                tokenTime.timeInMillis = date

                if (tokenTime.before(Calendar.getInstance())) {
                    logger.debug("Invalidating outdated token: $value")

                    intRedis.del(refreshArray[0]) { delResult ->
                        if (delResult.failed()) {
                            logger.error("Failed invalidating: $value", delResult.cause())
                        }
                    }

                    intRedis.hdel(registryKey, key) { delResult ->
                        if (delResult.failed()) {
                            logger.error("Failed invalidating jwt: $key", delResult.cause())
                        }
                    }
                }
            }
        } else {
            logger.error("JWT List for $userId is null...",
                    NullPointerException("UserId is Null!"))
        }
    }

    override fun close() {
        redisClient.close { closeResult -> logger.debug("Closed Redis for Service: " + closeResult.succeeded()) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VerificationServiceImpl::class.java!!.getSimpleName())

        private var KEY_ALGORITHM = "HmacSHA512"
    }
}
