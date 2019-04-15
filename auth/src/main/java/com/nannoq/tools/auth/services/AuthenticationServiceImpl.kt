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

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.nannoq.tools.auth.AuthGlobals.GLOBAL_AUTHORIZATION
import com.nannoq.tools.auth.AuthGlobals.JWT_CLAIMS_EMAIL_VERIFIED
import com.nannoq.tools.auth.AuthGlobals.JWT_CLAIMS_FAMILY_NAME
import com.nannoq.tools.auth.AuthGlobals.JWT_CLAIMS_GIVEN_NAME
import com.nannoq.tools.auth.AuthGlobals.JWT_CLAIMS_NAME
import com.nannoq.tools.auth.AuthGlobals.JWT_CLAIMS_USER_EMAIL
import com.nannoq.tools.auth.AuthGlobals.VALID_JWT_REGISTRY_KEY
import com.nannoq.tools.auth.models.AuthPackage
import com.nannoq.tools.auth.models.TokenContainer
import com.nannoq.tools.auth.models.UserProfile
import com.nannoq.tools.auth.services.providers.FaceBookProvider
import com.nannoq.tools.auth.services.providers.Google
import com.nannoq.tools.auth.services.providers.InstaGram
import com.nannoq.tools.auth.services.providers.utils.GoogleUser
import com.nannoq.tools.auth.utils.AuthFutures.authFail
import com.nannoq.tools.auth.utils.PermissionPack
import com.nannoq.tools.repository.models.ModelUtils
import com.nannoq.tools.repository.repository.redis.RedisUtils
import io.jsonwebtoken.*
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.DecodeException
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.redis.RedisClient
import io.vertx.serviceproxy.ServiceException
import io.vertx.serviceproxy.ServiceException.fail
import org.apache.commons.codec.digest.DigestUtils
import org.apache.logging.log4j.core.config.plugins.convert.HexConverter.parseHexBinary
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors.toConcurrentMap
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Suppress("unused")
/**
 * This class defines an authenticator. The authenticator receives an external token and converts it into an internal
 * JWT.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class AuthenticationServiceImpl @Throws(InvalidKeyException::class, NoSuchAlgorithmException::class)
constructor(vertx: Vertx, appConfig: JsonObject,
            private val setPermissionOnClaims: Function<PermissionPack, MutableMap<String, Any>>,
            KEY_BASE: String) : AuthenticationService {
    private val CALLBACK_URL: String
    private val EMAIL_HASH_KEY_BASE: String

    private val ISSUER: String
    private val AUDIENCE: String

    private val redisClient: RedisClient = RedisUtils.getRedisClient(vertx, appConfig)
    private val domainIdentifier: String = appConfig.getString("domainIdentifier")
    private val SIGNING_KEY: SecretKey

    private var notBeforeTimeInMinutes = -5
    private var idTokenExpirationInDays = 5
    private var refreshTokenExpirationInDays = 30
    private val googleProvider: Google
    private val facebookProvider: FaceBookProvider
    private val instaGramProvider: InstaGram

    init {
        this.SIGNING_KEY = SecretKeySpec(parseHexBinary(KEY_BASE), KEY_ALGORITHM)
        this.EMAIL_HASH_KEY_BASE = appConfig.getString("emailHashKeybase")
        val CALL_BACK_PROVIDER_URL = appConfig.getString("callbackProviderUrl")
        this.CALLBACK_URL = appConfig.getString("callBackRoot") + CALL_BACK_PROVIDER_URL
        this.ISSUER = appConfig.getString("authJWTIssuer")
        this.AUDIENCE = appConfig.getString("authJWTAudience")
        this.googleProvider = Google(vertx)
        val GOOGLE_CLIENT_IDS = appConfig.getJsonArray("gcmIds").list.map { it.toString() }
        this.googleProvider.withClientIds(GOOGLE_CLIENT_IDS)
        this.facebookProvider = FaceBookProvider(vertx, appConfig)
        this.instaGramProvider = InstaGram(vertx, appConfig, CALLBACK_URL)

        initializeKey(KEY_ALGORITHM)
    }

    @Fluent
    private fun setNotBeforeTimeInMinutes(notBeforeTimeInMinutes: Int): AuthenticationServiceImpl {
        this.notBeforeTimeInMinutes = notBeforeTimeInMinutes

        return this
    }

    @Fluent
    private fun setIdTokenExpirationInDays(idTokenExpirationInDays: Int): AuthenticationServiceImpl {
        this.idTokenExpirationInDays = idTokenExpirationInDays

        return this
    }

    @Fluent
    private fun setRefreshTokenExpirationInDays(refreshTokenExpirationInDays: Int): AuthenticationServiceImpl {
        this.refreshTokenExpirationInDays = refreshTokenExpirationInDays

        return this
    }

    @Fluent
    private fun setKeyAlgorithm(keyAlgorithm: String): AuthenticationServiceImpl {
        KEY_ALGORITHM = keyAlgorithm

        return this
    }

    @Fluent
    fun withNotBeforeTimeInMinutes(notBeforeTime: Int): AuthenticationServiceImpl {
        return setNotBeforeTimeInMinutes(notBeforeTime)
    }

    @Fluent
    fun withIdTokenExpirationInDays(idTokenExpiration: Int): AuthenticationServiceImpl {
        return setIdTokenExpirationInDays(idTokenExpiration)
    }

    @Fluent
    fun withRefreshTokenExpirationInDays(refreshTokenExpiration: Int): AuthenticationServiceImpl {
        return setRefreshTokenExpirationInDays(refreshTokenExpiration)
    }

    @Fluent
    fun withKeyAlgorithm(keyAlgorithm: String): AuthenticationServiceImpl {
        return setKeyAlgorithm(keyAlgorithm)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    private fun initializeKey(keyAlgorithm: String) {
        val mac = Mac.getInstance(keyAlgorithm)
        mac.init(SIGNING_KEY)
    }

    @Fluent
    override fun createJwtFromProvider(token: String, authProvider: String,
                                       resultHandler: Handler<AsyncResult<AuthPackage>>): AuthenticationService {
        val unableToParseException = fail<AuthPackage>(500, "Unable to parse Token: ")

        when (authProvider.toUpperCase()) {
            GOOGLE -> googleProvider.checkJWT(token, Handler {
                when {
                    it.failed() -> {
                        logger.error("Unable to process Google Token!", it.cause())

                        resultHandler.handle(unableToParseException)
                    }
                    else -> buildAuthPackage(it.result(), Handler { result ->
                        resultHandler.handle(succeededFuture(result.result()))
                    })
                }
            })
            FACEBOOK -> facebookProvider.checkJWT(token, Handler {
                when {
                    it.failed() -> {
                        logger.error("Unable to process Facebook Token!", it.cause())

                        resultHandler.handle(unableToParseException)
                    }
                    else -> buildAuthPackage(it.result(), Handler { result ->
                        resultHandler.handle(succeededFuture(result.result()))
                    })
                }
            })
            INSTAGRAM -> instaGramProvider.checkJWT(token, Handler {
                when {
                    it.failed() -> {
                        logger.error("Unable to process Instagram Token!", it.cause())

                        resultHandler.handle(unableToParseException)
                    }
                    else -> buildAuthPackage(it.result(), Handler { result ->
                        resultHandler.handle(succeededFuture(result.result()))
                    })
                }
            })
            else -> {
                logger.error("ERROR JwtGenerator: Unknown AuthProvider: $authProvider")
                resultHandler.handle(fail(400, "Unknown Provider..."))
            }
        }

        return this
    }

    private fun buildAuthPackage(userProfile: UserProfile, resultHandler: Handler<AsyncResult<AuthPackage>>) {
        val userId = try {
            ModelUtils.hashString(userProfile.email + EMAIL_HASH_KEY_BASE)
        } catch (e: NoSuchAlgorithmException) {
            logger.error("No such alg", e)

            userProfile.email
        }

        userProfile.userId = userId

        val claimsMap = createClaimsMap(userProfile)
        val email = userProfile.email

        doTokenCreation(userProfile, resultHandler, claimsMap, email)
    }

    private fun buildAuthPackage(result: GoogleIdToken.Payload,
                                 resultHandler: Handler<AsyncResult<AuthPackage>>) {
        val claims = mutableMapOf<String, Any>()
        claims[JWT_CLAIMS_USER_EMAIL] = result.email
        claims[JWT_CLAIMS_NAME] = result["name"] ?: "N/A"
        claims[JWT_CLAIMS_GIVEN_NAME] = result["given_name"] ?: "N/A"
        claims[JWT_CLAIMS_FAMILY_NAME] = result["family_name"] ?: "N/A"
        claims[JWT_CLAIMS_EMAIL_VERIFIED] = result["email_verified"] ?: "N/A"

        val userProfile = GoogleUser(result)
        val userId = try {
            ModelUtils.hashString(userProfile.email + EMAIL_HASH_KEY_BASE)
        } catch (e: NoSuchAlgorithmException) {
            logger.error("No such alg", e)

            userProfile.email
        }

        userProfile.userId = userId

        val email = result.email

        doTokenCreation(userProfile, resultHandler, claims, email)
    }

    private fun doTokenCreation(userProfile: UserProfile, resultHandler: Handler<AsyncResult<AuthPackage>>,
                                claimsMap: MutableMap<String, Any>, email: String) {
        createTokenContainer(email, claimsMap, Handler {
            when {
                it.result() != null -> resultHandler.handle(succeededFuture(AuthPackage(it.result(), userProfile)))
                else -> {
                    logger.error("TokenContainer is null...", it.cause())

                    resultHandler.handle(fail(500, "TokenContainer is null..."))
                }
            }
        })
    }

    private fun createClaimsMap(result: UserProfile): MutableMap<String, Any> {
        val claims = HashMap<String, Any>()
        claims[JWT_CLAIMS_USER_EMAIL] = result.email
        claims[JWT_CLAIMS_NAME] = result.name
        claims[JWT_CLAIMS_GIVEN_NAME] = result.givenName
        claims[JWT_CLAIMS_FAMILY_NAME] = result.familyName
        claims[JWT_CLAIMS_EMAIL_VERIFIED] = result.isEmailVerified

        return claims
    }

    private fun createTokenContainer(email: String, claims: MutableMap<String, Any>,
                                     resultHandler: Handler<AsyncResult<TokenContainer>>) {
        var newClaims = claims
        try {
            val id = ModelUtils.hashString(email + EMAIL_HASH_KEY_BASE)

            val now = Calendar.getInstance()
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, idTokenExpirationInDays)

            val notBefore = Calendar.getInstance()
            notBefore.add(Calendar.MINUTE, notBeforeTimeInMinutes)

            val jwtId = UUID.randomUUID().toString()

            claims["id"] = jwtId
            newClaims = generatePermissions(id, newClaims, GLOBAL_AUTHORIZATION)

            val jwt = createJwt(id, jwtId, newClaims, now.time, notBefore.time, calendar.time)

            calendar.add(Calendar.DAY_OF_MONTH, refreshTokenExpirationInDays)
            val newRefreshToken = DigestUtils.sha1Hex(id + UUID.randomUUID().toString())
            val refreshWithExpireKey = newRefreshToken + REFRESH_TOKEN_SPLITTER + calendar.time.time

            createTokenContainer(id, jwtId, email, newRefreshToken, newClaims, jwt, refreshWithExpireKey, resultHandler)
        } catch (e: JwtException) {
            logger.error("Failed Token Container Creation!", e)

            resultHandler.handle(fail(500, "" + e))
        } catch (e: IllegalArgumentException) {
            logger.error("Failed Token Container Creation!", e)

            resultHandler.handle(fail(500, "" + e))
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Failed Token Container Creation!", e)

            resultHandler.handle(fail(500, "" + e))
        }

    }

    private fun createTokenContainer(id: String, jwtId: String, email: String,
                                     newRefreshToken: String, claims: Map<String, Any>,
                                     jwt: String, expireToken: String,
                                     resultHandler: Handler<AsyncResult<TokenContainer>>) {
        val mapId = id + VALID_JWT_REGISTRY_KEY

        RedisUtils.performJedisWithRetry(redisClient) { intRedis ->
            val transaction = intRedis.transaction()

            transaction.multi {
                transaction.hset(mapId, jwtId, expireToken) { result ->
                    when {
                        result.failed() -> logger.error("Could not set valid jwt for: $email", result.cause())
                        else -> {
                            val encodedClaims = Json.encode(claims)

                            transaction.set(newRefreshToken, encodedClaims) {
                                if (it.failed()) {
                                    logger.error("Could not store refreshtoken for: $email", it.cause())
                                }
                            }
                        }
                    }
                }
            }

            transaction.exec { execResult ->
                when {
                    execResult.failed() -> resultHandler.handle(fail(500, "Could not set valid jwt for: $email"))
                    else -> resultHandler.handle(succeededFuture(TokenContainer(jwt, newRefreshToken)))
                }
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun createJwt(
            id: String,
            jwtId: String,
            claims: Map<String, Any>,
            now: Date,
            notBefore: Date,
            then: Date): String {
        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(ISSUER)
                .setSubject(id)
                .setAudience(AUDIENCE)
                .setExpiration(then)
                .setNotBefore(notBefore)
                .setIssuedAt(now)
                .setId(jwtId)
                .signWith(SignatureAlgorithm.HS512, SIGNING_KEY)
                .compressWith(CompressionCodecs.DEFLATE)
                .compact()
    }

    @Throws(IllegalArgumentException::class)
    private fun createJwt(
            id: String,
            jwtId: String,
            claims: Jws<Claims>,
            now: Date,
            notBefore: Date,
            then: Date): String {
        return Jwts.builder()
                .setClaims(claims.body)
                .setIssuer(ISSUER)
                .setSubject(id)
                .setAudience(AUDIENCE)
                .setExpiration(then)
                .setNotBefore(notBefore)
                .setIssuedAt(now)
                .setId(jwtId)
                .signWith(SignatureAlgorithm.HS512, SIGNING_KEY)
                .compressWith(CompressionCodecs.DEFLATE)
                .compact()
    }

    private fun generatePermissions(
            userId: String,
            claims: MutableMap<String, Any>,
            authOrigin: String): MutableMap<String, Any> {
        claims.putIfAbsent(domainIdentifier, userId)

        return setPermissionOnClaims.apply(PermissionPack(userId, claims, authOrigin))
    }

    @Fluent
    override fun refresh(refreshToken: String,
                         resultHandler: Handler<AsyncResult<TokenContainer>>): AuthenticationService {
        getTokenCache(refreshToken).compose({
            getClaims(it).compose({ map ->
                val oldId = map["id"].toString()

                getTokenContainer(map).compose({ tokenContainer ->
                    deleteOld(map, refreshToken, oldId, tokenContainer).compose({
                        container -> resultHandler.handle(succeededFuture(container))
                    }, authFail<Any, TokenContainer>(resultHandler))
                }, authFail<Any, TokenContainer>(resultHandler))
            }, authFail<Any, TokenContainer>(resultHandler))
        }, authFail<Any, TokenContainer>(resultHandler))

        return this
    }

    private fun deleteOld(claims: MutableMap<String, Any>, refreshToken: String, oldId: String,
                          tokenContainer: TokenContainer): Future<TokenContainer> {
        val tokenContainerFuture = future<TokenContainer>()
        val email = claims[JWT_CLAIMS_USER_EMAIL].toString()
        val userId = try {
            ModelUtils.hashString(email + EMAIL_HASH_KEY_BASE)
        } catch (e: NoSuchAlgorithmException) {
            logger.error("No Algorithm!", e)

            email
        }

        logger.debug("Purging: $userId$VALID_JWT_REGISTRY_KEY $oldId")

        val registry = userId + VALID_JWT_REGISTRY_KEY

        RedisUtils.performJedisWithRetry(redisClient) {
            val transaction = it.transaction()

            transaction.multi {
                transaction.del(refreshToken) { result ->
                    if (result.failed()) { logger.debug("Del RefreshToken failed!") }
                }

                transaction.hdel(registry, oldId) { result ->
                    if (result.failed()) { logger.debug("Del JwtValidity failed!") }
                }
            }

            transaction.exec { result ->
                when {
                    result.failed() -> tokenContainerFuture.fail(InternalError("Unable to purge old refresh..."))
                    else -> {
                        logger.debug("Purged all remnants of old refresh...")

                        tokenContainerFuture.complete(tokenContainer)
                    }
                }
            }
        }

        return tokenContainerFuture
    }

    private fun getClaims(tokenCache: String?): Future<MutableMap<String, Any>> {
        val claimsFuture = future<MutableMap<String, Any>>()

        when (tokenCache) {
            null -> claimsFuture.fail(ServiceException(500, "TokenCache cannot be null..."))
            else -> try {
                val claims = Json.decodeValue(tokenCache, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                claimsFuture.complete(claims as MutableMap<String, Any>?)
            } catch (e: DecodeException) {
                claimsFuture.fail(e)
            }
        }

        return claimsFuture
    }

    private fun getTokenContainer(claims: MutableMap<String, Any>): Future<TokenContainer> {
        val tokenContainerFuture = future<TokenContainer>()
        val email = claims[JWT_CLAIMS_USER_EMAIL].toString()

        createTokenContainer(email, claims, Handler {
            when {
                it.failed() -> tokenContainerFuture.fail(it.cause())
                else -> tokenContainerFuture.complete(it.result())
            }
        })

        return tokenContainerFuture
    }

    private fun getTokenCache(refreshToken: String): Future<String> {
        val tokenCacheFuture = future<String>()

        RedisUtils.performJedisWithRetry(redisClient) {
            it.get(refreshToken) { getResult ->
                when {
                    getResult.failed() -> tokenCacheFuture.fail(getResult.cause())
                    else -> tokenCacheFuture.complete(getResult.result())
                }
            }
        }

        return tokenCacheFuture
    }

    @Fluent
    override fun switchToAssociatedDomain(domainId: String, verifyResult: Jws<Claims>,
                                          resultHandler: Handler<AsyncResult<TokenContainer>>): AuthenticationService {
        verifyResult.body[domainIdentifier] = domainId

        createTokenContainer(verifyResult, resultHandler)

        return this
    }

    private fun createTokenContainer(claims: Jws<Claims>, resultHandler: Handler<AsyncResult<TokenContainer>>) {
        try {
            val email = claims.body[JWT_CLAIMS_USER_EMAIL].toString()
            val id = ModelUtils.hashString(email + EMAIL_HASH_KEY_BASE)

            val now = Calendar.getInstance()
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, idTokenExpirationInDays)

            val notBefore = Calendar.getInstance()
            notBefore.add(Calendar.MINUTE, notBeforeTimeInMinutes)

            val jwtId = UUID.randomUUID().toString()

            claims.body["id"] = jwtId

            val jwt = createJwt(id, jwtId, claims, now.time, notBefore.time, calendar.time)

            calendar.add(Calendar.DAY_OF_MONTH, refreshTokenExpirationInDays)
            val newRefreshToken = DigestUtils.sha1Hex(id + UUID.randomUUID().toString())
            val refreshTokenWithExpireKey = newRefreshToken + REFRESH_TOKEN_SPLITTER + calendar.time.time

            val mappedClaims = claims.body.entries.stream()
                    .map<AbstractMap.SimpleEntry<String, Any>> { e -> AbstractMap.SimpleEntry(e.key, e.value) }
                    .collect(toConcurrentMap<AbstractMap.SimpleEntry<String, Any>, String, Any>({ it.key }) { it.value })

            createTokenContainer(id, jwtId, email, newRefreshToken, mappedClaims,
                    jwt, refreshTokenWithExpireKey, resultHandler)
        } catch (e: JwtException) {
            logger.error("Failed Token Container Creation!", e)

            resultHandler.handle(fail(500, "" + e))
        } catch (e: IllegalArgumentException) {
            logger.error("Failed Token Container Creation!", e)

            resultHandler.handle(fail(500, "" + e))
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Failed Token Container Creation!", e)

            resultHandler.handle(fail(500, "" + e))
        }
    }

    override fun close() {
        redisClient.close { logger.debug("RedisClient closed for AuthenticationService: " + it.succeeded()) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthenticationServiceImpl::class.java.simpleName)

        private var KEY_ALGORITHM = "HmacSHA512"

        const val GOOGLE = "GOOGLE"
        const val FACEBOOK = "FACEBOOK"
        const val INSTAGRAM = "INSTAGRAM"

        internal const val REFRESH_TOKEN_SPLITTER = "____"
    }
}
