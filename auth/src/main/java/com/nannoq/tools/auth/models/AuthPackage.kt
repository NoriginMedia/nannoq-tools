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

package com.nannoq.tools.auth.models

import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject

/**
 * This class defines a container for a TokenContainer and a UserProfile
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@DataObject(generateConverter = true)
class AuthPackage {
    val tokenContainer: TokenContainer
    val userProfile: UserProfile

    constructor(tokenContainer: TokenContainer, userProfile: UserProfile) {
        this.tokenContainer = tokenContainer
        this.userProfile = userProfile
    }

    constructor(jsonObject: JsonObject) {
        this.tokenContainer = TokenContainer(jsonObject.getJsonObject("tokenContainer"))
        this.userProfile = Json.decodeValue<UserProfile>(
                (jsonObject.getJsonObject("userProfile") ?: JsonObject()).encode(), UserProfile::class.java)
    }

    fun toJson(): JsonObject {
        return JsonObject()
                .put("tokenContainer", tokenContainer.toJson())
                .put("userProfile", userProfile.toJson())
    }
}
