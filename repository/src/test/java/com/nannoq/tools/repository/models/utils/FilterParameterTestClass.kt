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

package com.nannoq.tools.repository.models.utils

import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.models.ValidationError
import io.vertx.core.json.JsonObject
import java.util.Date

class FilterParameterTestClass : Model {
    private val viewCount: Long? = null

    override var createdAt: Date?
        get() = Date()
        set(value) {}
    override var updatedAt: Date?
        get() = Date()
        set(value) {}

    override fun setIdentifiers(identifiers: JsonObject): Model {
        return this
    }

    override fun setModifiables(newObject: Model): Model {
        return this
    }

    override fun sanitize(): Model {
        return this
    }

    override fun validateCreate(): List<ValidationError> {
        return listOf()
    }

    override fun validateUpdate(): List<ValidationError> {
        return listOf()
    }

    override fun setCreatedAt(date: Date): Model {
        return this
    }

    override fun setUpdatedAt(date: Date): Model {
        return this
    }

    override fun setInitialValues(record: Model): Model {
        return this
    }

    override fun toJsonFormat(projections: Array<String>): JsonObject {
        return JsonObject()
    }
}
