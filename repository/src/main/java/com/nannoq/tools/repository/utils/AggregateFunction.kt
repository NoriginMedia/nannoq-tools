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
import com.nannoq.tools.repository.utils.AggregateFunctions.COUNT
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.util.*
import java.util.stream.Collectors.toList

/**
 * This class defines an aggregation function with the field, the function, and any grouping parameters.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class AggregateFunction {
    var function: AggregateFunctions? = null
        private set
    var field: String? = null
    var groupBy: List<GroupingConfiguration>? = null
        private set

    @JsonIgnore
    val validationError: JsonObject = JsonObject()

    val isMin: Boolean
        get() = function == AggregateFunctions.MIN

    val isMax: Boolean
        get() = function == AggregateFunctions.MAX

    val isAverage: Boolean
        get() = function == AggregateFunctions.AVG

    val isSum: Boolean
        get() = function == AggregateFunctions.SUM

    val isCount: Boolean
        get() = function == COUNT

    enum class TIMEUNIT_DATE {
        HOUR, TWELVE_HOUR, DAY, WEEK, MONTH, YEAR
    }

    init {
        this.groupBy = LinkedList()
    }

    class AggregateFunctionBuilder internal constructor() {

        private var function: AggregateFunctions? = null
        private var field: String? = null
        private var groupBy: MutableList<GroupingConfiguration>? = LinkedList()

        fun build(): AggregateFunction {
            if (function == null) {
                throw IllegalArgumentException("Function cannot be null!")
            }

            if (field == null && function != COUNT) {
                throw IllegalArgumentException("Field cannot be null!")
            }

            val func = AggregateFunction()
            func.function = function
            func.field = field
            func.groupBy = if (groupBy == null) LinkedList() else groupBy

            return func
        }

        @Fluent
        fun withAggregateFunction(function: AggregateFunctions): AggregateFunctionBuilder {
            this.function = function

            return this
        }

        @Fluent
        fun withField(field: String?): AggregateFunctionBuilder {
            this.field = field

            return this
        }

        @Fluent
        fun withGroupBy(groupBy: List<GroupingConfiguration>): AggregateFunctionBuilder {
            if (groupBy.size > 3) {
                throw IllegalArgumentException("You can only group 3 levels deep!")
            }

            if (this.groupBy == null) this.groupBy = LinkedList()
            this.groupBy!!.addAll(groupBy)

            return this
        }

        @Fluent
        fun addGroupBy(groupBy: GroupingConfiguration): AggregateFunctionBuilder {
            if (this.groupBy == null) this.groupBy = LinkedList()

            if (this.groupBy!!.size == 3) {
                throw IllegalArgumentException("You can only group 3 levels deep!")
            }

            this.groupBy!!.add(groupBy)

            return this
        }

        companion object {
            private val logger = LoggerFactory.getLogger(FilterParameter.FilterParameterBuilder::class.java.simpleName)
        }
    }

    fun hasGrouping(): Boolean {
        return !groupBy!!.isEmpty()
    }

    fun <E : ETagable> validateFieldForFunction(TYPE: Class<E>): Boolean {
        if (!isMin && !isMax && !isAverage) return true

        if (field == null) {
            val errorMessage = "Field name cannot be null..."

            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (function) {
                AggregateFunctions.MIN -> validationError.put("min_error", errorMessage)
                AggregateFunctions.MAX -> validationError.put("max_error", errorMessage)
                AggregateFunctions.AVG -> validationError.put("avg_error", errorMessage)
            }

            return false
        }

        if (hasGrouping()) {
            val collect = groupBy!!.stream()
                    .map { groupingConfiguration -> groupingConfiguration.validate(TYPE, field!!, validationError) }
                    .collect(toList())

            return collect.stream().anyMatch { res -> !res }
        }

        return validationError.isEmpty
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as AggregateFunction?

        return function == that!!.function &&
                field == that.field &&
                groupBy == that.groupBy
    }

    override fun hashCode(): Int {
        return Objects.hash(function, field, groupBy)
    }

    companion object {

        fun builder(): AggregateFunctionBuilder {
            return AggregateFunctionBuilder()
        }
    }
}
