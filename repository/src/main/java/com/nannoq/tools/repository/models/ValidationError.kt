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

package com.nannoq.tools.repository.models

import com.fasterxml.jackson.annotation.JsonIgnore
import io.vertx.codegen.annotations.DataObject
import io.vertx.core.json.JsonObject
import java.util.*

/**
 * This class defines helpers for Model operations.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@DataObject(generateConverter = true)
class ValidationError {

    var description: String? = null
        private set
    var fieldName: String? = null
        private set

    constructor() {}

    constructor(description: String, fieldName: String) {
        this.description = description
        this.fieldName = fieldName
    }

    constructor(jsonObject: JsonObject) {
        description = jsonObject.getString("description")
        fieldName = jsonObject.getString("fieldName")
    }

    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    companion object {
        @JsonIgnore
        private val DAY = 86400000L

        fun validateNotNull(o: Any?, fieldName: String): ValidationError? {
            return if (o == null) ValidationError("Cannot be null!", fieldName) else null
        }

        fun validateDate(date: Date?, fieldName: String): ValidationError? {
            if (date == null) return ValidationError("Date cannot be null!", fieldName)

            val yesterday = Calendar.getInstance()
            yesterday.timeInMillis = yesterday.time.time - DAY

            return if (date.before(yesterday.time)) {
                ValidationError("Cannot be older than 24H!", fieldName)
            } else null

        }

        fun validateTextLength(field: String?, fieldName: String, count: Int): ValidationError? {
            if (field == null) return ValidationError("Field cannot be null!", fieldName)

            return if (field.length > count) {
                ValidationError("Cannot be over $count characters!", fieldName)
            } else null

        }
    }
}
