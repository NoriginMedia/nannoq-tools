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

package com.nannoq.tools.repository.utils

import com.nannoq.tools.repository.models.ValidationError
import org.apache.commons.lang3.StringUtils
import java.util.*

/**
 * This class defines projection configurations for cross table queries.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class CrossTableProjection @JvmOverloads constructor(val models: List<String>? = null, TABLES: List<String>? = null, val fields: List<String>? = null) {
    private var TABLES: List<String>? = null

    init {
        this.TABLES = TABLES
    }

    fun setTABLES(TABLES: List<String>) {
        this.TABLES = TABLES
    }

    fun validate(function: AggregateFunctions): List<ValidationError> {
        val errors = ArrayList<ValidationError>()

        models?.forEach { model ->
            if (!TABLES!!.contains(model)) {
                errors.add(ValidationError(
                        "$model is not a valid model!",
                        "models_$model"))
            }
        } ?: errors.add(ValidationError(
                "models_error", "Models cannot be null for!"))

        when {
            fields == null && function != AggregateFunctions.COUNT -> errors.add(ValidationError(
                    "fields_error", "Fields cannot be null for: " + function.name))
            fields != null && function != AggregateFunctions.COUNT -> fields.forEach { field ->
                if (StringUtils.countMatches(field, ".") != 1) {
                    errors.add(ValidationError(
                            "$field invalid! Must be in this format: <modelNamePluralized>.<fieldName>",
                            "fields_$field"))
                }

                val fieldSplit = field.split("\\.".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()

                if (!fieldSplit[0].endsWith("s")) {
                    errors.add(ValidationError(
                            "$field is not pluralized!", "fields_$field"))
                }
            }
            fields != null -> errors.add(ValidationError(
                    "fields_error", "Fields must be null for: " + function.name))
        }

        return errors
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as CrossTableProjection?

        return TABLES == that!!.TABLES &&
                models == that.models &&
                fields == that.fields
    }

    override fun hashCode(): Int {
        return Objects.hash(TABLES, models, fields)
    }
}
