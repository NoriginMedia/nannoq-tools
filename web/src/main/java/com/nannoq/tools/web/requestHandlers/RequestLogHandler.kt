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

package com.nannoq.tools.web.requestHandlers

import io.vertx.core.Handler
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import org.apache.commons.lang3.exception.ExceptionUtils
import java.rmi.server.UID

/**
 * This interface defines the RequestLogHandler. It starts the logging process, to be concluded by the
 * responseloghandler.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class RequestLogHandler : Handler<RoutingContext> {

    override fun handle(routingContext: RoutingContext) {
        routingContext.put(REQUEST_PROCESS_TIME_TAG, System.nanoTime())
        val uniqueToken = UID().toString()
        routingContext.put(REQUEST_ID_TAG, uniqueToken)
        val sb = StringBuffer()

        sb.append("\n--- ").append("Start Request Logging: ").append(uniqueToken).append(" ---\n")
        sb.append("\n--- ").append(routingContext.request().rawMethod())
                .append(" : ")
                .append(routingContext.request().absoluteURI()).append(" ")
                .append(uniqueToken).append(" ---\n")
        sb.append("\n--- ").append("Logging Frame: ").append(uniqueToken).append(" ---\n")
        routingContext.put(REQUEST_LOG_TAG, sb)

        routingContext.next()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RequestLogHandler::class.java.simpleName)

        const val REQUEST_PROCESS_TIME_TAG = "processTimeTag"
        const val REQUEST_ID_TAG = "uniqueRequestId"
        const val REQUEST_LOG_TAG = "requestLog"

        @JvmOverloads
        fun addLogMessageToRequestLog(routingContext: RoutingContext, message: String, t: Throwable? = null) {
            val sb = routingContext.get<StringBuffer>(REQUEST_LOG_TAG)

            if (sb != null) {
                sb.append("\n\n").append(message).append("\n\n")

                if (t != null) {
                    sb.append("\n\n").append(ExceptionUtils.getStackTrace(t)).append("\n\n")
                }
            } else {
                logger.warn("Routinglogger not available, printing: $message", t)
            }
        }
    }
}
