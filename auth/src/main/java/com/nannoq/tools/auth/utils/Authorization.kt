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

package com.nannoq.tools.auth.utils

import com.nannoq.tools.auth.AuthGlobals.GLOBAL_AUTHORIZATION
import com.nannoq.tools.auth.AuthGlobals.VALIDATION_REQUEST
import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject

/**
 * This class defines the object sent to the VerificationService to authorize a request. Currently support method based
 * on models, with an optional domainIdentifier to authorize creators.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@DataObject(generateConverter = true)
class Authorization {
    val model: String
    val method: String
    val domainIdentifier: String

    constructor() : this(JsonObject())

    constructor(jsonObject: JsonObject) {
        this.model = jsonObject.getString("model")
        this.method = jsonObject.getString("method")
        this.domainIdentifier = jsonObject.getString("domainIdentifier")
    }

    constructor(model: String, method: String, domainIdentifier: String) {
        this.model = model
        this.method = method
        this.domainIdentifier = domainIdentifier
    }

    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    @Suppress("SENSELESS_COMPARISON")
    fun validate(): Boolean {
        return domainIdentifier != null && (domainIdentifier == VALIDATION_REQUEST ||
                domainIdentifier == GLOBAL_AUTHORIZATION) ||
                model != null && method != null && domainIdentifier != null
    }

    companion object {
        fun global(): Authorization {
            return Authorization(JsonObject().put("domainIdentifier", VALIDATION_REQUEST))
        }
    }
}
