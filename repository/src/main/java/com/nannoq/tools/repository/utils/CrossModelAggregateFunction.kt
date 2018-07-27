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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.nannoq.tools.repository.models.ETagable
import io.vertx.core.json.JsonObject
import java.util.*
import java.util.stream.Collectors.toList

/**
 * This class defines an aggregation function with the field, the function, and any grouping parameters for using with
 * multiple models.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class CrossModelAggregateFunction {
    var function: AggregateFunctions? = null
    var field: String? = null
    var groupBy: List<CrossModelGroupingConfiguration>? = null
        private set

    @JsonIgnore
    var validationError: JsonObject? = null
        private set

    val isMin: Boolean
        get() = function == AggregateFunctions.MIN

    val isMax: Boolean
        get() = function == AggregateFunctions.MAX

    val isAverage: Boolean
        get() = function == AggregateFunctions.AVG

    val isSum: Boolean
        get() = function == AggregateFunctions.SUM

    val isCount: Boolean
        get() = function == AggregateFunctions.COUNT

    private enum class TIMEUNIT_DATE {
        HOUR, TWELVE_HOUR, DAY, WEEK, MONTH, YEAR
    }

    constructor() {
        this.validationError = JsonObject()
        this.groupBy = ArrayList()
    }

    @JvmOverloads constructor(function: AggregateFunctions, field: String, groupBy: List<CrossModelGroupingConfiguration>? = ArrayList()) {
        this.function = function
        this.field = field
        this.groupBy = groupBy ?: ArrayList()
        this.validationError = JsonObject()
    }

    fun hasGrouping(): Boolean {
        return groupBy != null && groupBy!!.size > 0
    }

    fun <E : ETagable> validateFieldForFunction(TYPE: Class<E>): Boolean {
        if (!isMin && !isMax && !isAverage) return true

        if (field == null) {
            val errorMessage = "Field name cannot be null..."

            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (function) {
                AggregateFunctions.MIN -> validationError!!.put("min_error", errorMessage)
                AggregateFunctions.MAX -> validationError!!.put("max_error", errorMessage)
                AggregateFunctions.AVG -> validationError!!.put("avg_error", errorMessage)
            }

            return false
        }

        if (hasGrouping()) {
            val collect = groupBy!!.stream()
                    .map { groupingConfiguration -> groupingConfiguration.validate(TYPE, field!!, validationError!!) }
                    .collect(toList())

            return collect.stream().anyMatch { res -> !res }
        }

        return validationError!!.isEmpty
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as CrossModelAggregateFunction?

        return function == that!!.function &&
                field == that.field &&
                groupBy == that.groupBy
    }

    override fun hashCode(): Int {
        return Objects.hash(function, field, groupBy)
    }
}
