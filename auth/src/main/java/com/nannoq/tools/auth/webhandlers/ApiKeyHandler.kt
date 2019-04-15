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

package com.nannoq.tools.auth.webhandlers

import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext

/**
 * This class defines an auth handler for verifying against static API keys.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class ApiKeyHandler(private val apiKey: String) : Handler<RoutingContext> {
    override fun handle(routingContext: RoutingContext) {
        val incomingKey = routingContext.request().getHeader("Authorization")

        when {
            incomingKey.startsWith("APIKEY ") -> {
                val key = incomingKey.substring("APIKEY".length).trim { it <= ' ' }

                when (key) {
                    apiKey -> routingContext.next()
                    else -> unAuthorized(routingContext)
                }
            }
        }
    }

    private fun unAuthorized(routingContext: RoutingContext) {
        routingContext.fail(401)
    }
}
