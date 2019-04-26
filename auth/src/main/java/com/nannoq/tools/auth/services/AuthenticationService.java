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

import com.nannoq.tools.auth.models.AuthPackage;
import com.nannoq.tools.auth.models.TokenContainer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nonnull;

/**
 * This class defines the AuthenticationService interface. It is used for creating JWTS and refreshing them.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@SuppressWarnings({"UnusedReturnValue", "NullableProblems"})
@ProxyGen
@VertxGen
public interface AuthenticationService {
    @Fluent
    AuthenticationService createJwtFromProvider(@Nonnull String token, @Nonnull String authProvider,
                                                @Nonnull Handler<AsyncResult<AuthPackage>> resultHandler);

    @Fluent
    AuthenticationService refresh(@Nonnull String refreshToken,
                                  @Nonnull Handler<AsyncResult<TokenContainer>> resultHandler);

    @SuppressWarnings("unused")
    @Fluent
    @GenIgnore
    default AuthenticationService switchToAssociatedDomain(String domainId, Jws<Claims> verifyResult,
                                                           Handler<AsyncResult<TokenContainer>> resultHandler) {
        resultHandler.handle(Future.failedFuture(new NotImplementedException("NON-OP")));

        return this;
    }

    @SuppressWarnings("unused")
    @ProxyClose
    void close();
}
