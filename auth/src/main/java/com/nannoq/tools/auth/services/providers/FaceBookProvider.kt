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

package com.nannoq.tools.auth.services.providers

import com.nannoq.tools.auth.services.AuthenticationServiceImpl.Companion.FACEBOOK
import com.nannoq.tools.auth.services.providers.utils.FaceBookUser
import facebook4j.FacebookException
import facebook4j.FacebookFactory
import facebook4j.Reading
import facebook4j.auth.AccessToken
import facebook4j.conf.ConfigurationBuilder
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory

/**
 * This class defines a facebook provider which will check an incoming token and verify its authenticity towards
 * facebook and return a FaceBookUser object.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class FaceBookProvider(private val vertx: Vertx, appConfig: JsonObject) : Provider<FaceBookUser> {
    private val appId: String = appConfig.getString("faceBookAppId")
    private val appSecret: String = appConfig.getString("faceBookAppSecret")

    override fun checkJWT(token: String, resultHandler: Handler<AsyncResult<FaceBookUser>>) {
        vertx.executeBlocking<FaceBookUser>({
            val authToken = AccessToken(token)
            val cb = ConfigurationBuilder()
            cb.setAppSecretProofEnabled(true)
            cb.setOAuthAppId(appId)
            cb.setOAuthAppSecret(appSecret)
            val facebook = FacebookFactory(cb.build()).instance
            facebook.setOAuthPermissions("public_profile,email,user_friends")
            facebook.oAuthAccessToken = authToken

            try {
                val user = facebook.getMe(Reading().fields(READING_FIELDS))
                val faceBookUser = FaceBookUser(user)
                faceBookUser.pictureUrl = facebook.users().getPictureURL(user.id, 400, 400).toString()

                it.complete(faceBookUser)
            } catch (e: FacebookException) {
                logger.error("AUTH $FACEBOOK Error: $e")

                it.fail(UnknownError())
            }
        }, false) {
            when {
                it.succeeded() -> resultHandler.handle(Future.succeededFuture(it.result()))
                else -> resultHandler.handle(Future.failedFuture(it.cause()))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FaceBookProvider::class.java.simpleName)

        private const val READING_FIELDS = "id,email,name,first_name,middle_name,last_name,verified,picture"
    }
}
