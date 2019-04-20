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

import com.fasterxml.jackson.annotation.JsonInclude
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.Date

/**
 * This class defines the model interface which includes sanitation, validation and json representations.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
interface Model {
    var createdAt: Date?
    var updatedAt: Date?

    @Fluent
    fun setModifiables(newObject: Model): Model {
        return this
    }

    @Fluent
    fun sanitize(): Model {
        return this
    }

    fun validateCreate(): List<ValidationError> {
        return emptyList()
    }

    fun validateUpdate(): List<ValidationError> {
        return emptyList()
    }

    @Fluent
    fun setIdentifiers(identifiers: JsonObject): Model {
        return this
    }

    @Fluent
    fun setInitialValues(record: Model): Model {
        createdAt = record.createdAt
        updatedAt = record.updatedAt

        return this
    }

    fun toJson(): JsonObject {
        return JsonObject.mapFrom(this)
    }

    fun toJsonFormat(projections: Array<String>): JsonObject {
        val jsonObject = JsonObject(Json.encode(this))

        projections.forEach { jsonObject.remove(it) }

        return jsonObject
    }

    fun toJsonFormat(): JsonObject {
        return toJsonFormat(arrayOf())
    }

    fun toJsonString(): String {
        return Json.encode(toJsonFormat())
    }

    fun toJsonString(projections: Array<String>): String {
        return Json.encode(toJsonFormat(projections))
    }

    fun validateNotNullAndAdd(
        jsonObject: JsonObject,
        projectionList: List<String>,
        key: String,
        value: Any?
    ): JsonObject {
        if (value != null) {
            if (projectionList.isEmpty() || projectionList.contains(key)) {
                jsonObject.put(key, value)
            }
        }

        return jsonObject
    }

    companion object {
        fun buildValidationErrorObject(errors: List<ValidationError>): JsonObject {
            val errorObject = JsonObject()
            errorObject.put("error_type", "VALIDATION")
            val errorObjects = JsonArray()
            errors.stream().map<JsonObject> { it.toJson() }.forEach { errorObjects.add(it) }

            errorObject.put("errors", errorObjects)

            return errorObject
        }
    }
}
