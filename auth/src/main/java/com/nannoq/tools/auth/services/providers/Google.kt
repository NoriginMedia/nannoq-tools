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

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance
import com.nannoq.tools.auth.services.AuthenticationServiceImpl.Companion.GOOGLE
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * This class defines a google provider which will check an incoming token and verify its authenticity towards
 * google and return a GoogleUser object.
 *
 * @author Anders Mikkelsen
 * @version 13/11/17
 */
class Google(private val vertx: Vertx) : Provider<GoogleIdToken.Payload> {
    private var CLIENT_IDS: List<String>? = null
    private var verifier: GoogleIdTokenVerifier? = null

    @Fluent
    fun withClientIds(ids: List<String>?): Google {
        CLIENT_IDS = ids
        val jsonFactory = getDefaultInstance()
        val transport: HttpTransport

        try {
            transport = GoogleNetHttpTransport.newTrustedTransport()
            verifier = GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                    .setAudience(CLIENT_IDS)
                    .setIssuer("https://accounts.google.com")
                    .build()
        } catch (e: GeneralSecurityException) {
            logger.error(e)
        } catch (e: IOException) {
            logger.error(e)
        }

        return this
    }

    override fun checkJWT(token: String, resultHandler: Handler<AsyncResult<GoogleIdToken.Payload>>) {
        vertx.executeBlocking<GoogleIdToken.Payload>({
            val idToken: GoogleIdToken?

            try {
                idToken = verifier?.verify(token)

                logger.info(idToken)

                if (idToken == null) {
                    it.fail(RuntimeException("Could not verify JWT..."))
                } else {
                    it.complete(idToken.payload)
                }
            } catch (e: GeneralSecurityException) {
                logger.error("\nERROR " + GOOGLE + " Auth: " + e.message)

                it.fail(e)
            } catch (e: IOException) {
                logger.error("\nERROR " + GOOGLE + " Auth: " + e.message)
                it.fail(e)
            } catch (e: IllegalArgumentException) {
                logger.error("\nERROR " + GOOGLE + " Auth: " + e.message)
                it.fail(e)
            }
        }, false) {
            if (it.succeeded()) {
                resultHandler.handle(Future.succeededFuture<GoogleIdToken.Payload>(it.result()))
            } else {
                resultHandler.handle(Future.failedFuture<GoogleIdToken.Payload>(it.cause()))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Google::class.java!!.getSimpleName())
    }
}
