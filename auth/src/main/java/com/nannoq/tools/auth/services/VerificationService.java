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

package com.nannoq.tools.auth.services;

import com.nannoq.tools.auth.models.VerifyResult;
import com.nannoq.tools.auth.utils.Authorization;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.vertx.codegen.annotations.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nonnull;

/**
 * This class defines the VerificationService interface. It is used for verifying singular and all tokens, and revoking
 * JWT's.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@SuppressWarnings({"UnusedReturnValue", "NullableProblems", "unused"})
@ProxyGen
@VertxGen
public interface VerificationService {
    @Fluent
    VerificationService verifyJWT(@Nonnull String token, @Nonnull Authorization authorization,
                                  @Nonnull Handler<AsyncResult<VerifyResult>> resultHandler);

    @Fluent
    VerificationService revokeToken(@Nonnull String token, @Nonnull Handler<AsyncResult<Boolean>> resultHandler);

    @Fluent
    VerificationService verifyJWTValidity(@Nonnull Handler<AsyncResult<Boolean>> resultHandler);

    @Fluent
    VerificationService revokeUser(@Nonnull String userId, @Nonnull Handler<AsyncResult<Boolean>> resultHandler);

    @Fluent
    @GenIgnore
    default VerificationService verifyToken(@Nonnull String token, Handler<AsyncResult<Jws<Claims>>> resultHandler) {
        resultHandler.handle(Future.failedFuture(new NotImplementedException("NON-OP")));

        return this;
    }

    @SuppressWarnings("RedundantThrows")
    @Fluent
    @GenIgnore
    default VerificationService verifyAuthorization(Jws<Claims> claims, Authorization authorization,
                                                    Handler<AsyncResult<Boolean>> resultHandler)
            throws IllegalAccessException {
        resultHandler.handle(Future.failedFuture(new NotImplementedException("NON-OP")));

        return this;
    }

    @ProxyClose
    void close();
}
