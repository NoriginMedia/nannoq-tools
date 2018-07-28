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

package com.nannoq.tools.auth

/**
 * This class defines various globals for setting and extracting values. And the Global authorization value. The global
 * authorization value is used for checking the validity of a JWT as well any information available for all users. It is
 * applied as the domainIdentifier.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
object AuthGlobals {
    const val VALID_JWT_REGISTRY_KEY = "_valid_jwt_registry"
    const val VALIDATION_REQUEST = "VALIDATION"

    // auth
    const val GLOBAL_AUTHORIZATION = "GLOBAL"

    // claims
    const val JWT_CLAIMS_USER_EMAIL = "email"
    const val JWT_CLAIMS_NAME = "name"
    const val JWT_CLAIMS_GIVEN_NAME = "givenName"
    const val JWT_CLAIMS_FAMILY_NAME = "familyName"
    const val JWT_CLAIMS_EMAIL_VERIFIED = "emailVerified"

    // timers
    const val SEVEN_DAYS = 7L * (3600L * 1000L * 24L)
}
