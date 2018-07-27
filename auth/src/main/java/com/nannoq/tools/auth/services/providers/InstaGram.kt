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

import com.nannoq.tools.auth.services.AuthenticationServiceImpl.Companion.INSTAGRAM
import com.nannoq.tools.auth.services.providers.utils.InstaGramUser
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import org.jinstagram.Instagram
import org.jinstagram.auth.InstagramAuthService
import org.jinstagram.auth.model.Verifier
import org.jinstagram.auth.oauth.InstagramService
import org.jinstagram.exceptions.InstagramException

/**
 * This class defines an instagram provider which will check an incoming token and verify its authenticity towards
 * instagram and return an InstaGramUser object.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class InstaGram(private val vertx: Vertx, appConfig: JsonObject, callBackUrl: String) : Provider<InstaGramUser> {
    private val clientSecret: String
    private val instagramService: InstagramService

    init {
        val clientId = appConfig.getString("instaClientId")
        this.clientSecret = appConfig.getString("instaClientSecret")
        instagramService = InstagramAuthService()
                .apiKey(clientId)
                .apiSecret(clientSecret)
                .callback(callBackUrl)
                .scope("basic public_content follower_list likes comments relationships")
                .build()
    }

    override fun checkJWT(token: String, resultHandler: Handler<AsyncResult<InstaGramUser>>) {
        vertx.executeBlocking<InstaGramUser>({
            val instagramToken = instagramService.getAccessToken(Verifier(token))
            val instagram = Instagram(instagramToken.token, clientSecret)

            try {
                it.complete(InstaGramUser(instagram.currentUserInfo))
            } catch (e: InstagramException) {
                LoggerFactory.getLogger(InstaGram::class.java.simpleName).error("$INSTAGRAM ERROR: $e")

                it.complete(null)
            }
        }, false) {
            if (it.succeeded()) {
                resultHandler.handle(Future.succeededFuture(it.result()))
            } else {
                resultHandler.handle(Future.failedFuture(it.cause()))
            }
        }
    }
}
