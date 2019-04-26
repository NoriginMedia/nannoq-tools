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

import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * @author Anders Mikkelsen
 * @version 19.12.2017
 */
@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(VertxExtension::class)
class JWTGeneratorTest {

    companion object {
        @BeforeAll
        @Throws(Exception::class)
        @JvmStatic
        fun setUp(vertx: Vertx, testContext: VertxTestContext) {
            testContext.completeNow()
        }

        @AfterAll
        @Throws(Exception::class)
        @JvmStatic
        fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
            testContext.completeNow()
        }
    }

    @Test
    fun handle() {
    }

    @Test
    fun directAuth() {
    }

    @Test
    fun returnAuthUrl() {
    }

    @Test
    fun refreshFromHttp() {
    }
}