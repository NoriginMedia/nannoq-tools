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
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.util.Date
import java.util.Objects

/**
 * This class defines the grouping configuration for a single model, similar to the cross-model grouping configurations.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class GroupingConfiguration {
    var groupBy: String? = null
        private set
    var groupByUnit: String? = ""
        private set
    var groupByRange: Any? = null
        private set
    var groupingSortOrder = "desc"
        private set
    var groupingListLimit = 10
        get() = if (field == 0 || isFullList) Integer.MAX_VALUE else field
    @JsonIgnore
    var isFullList: Boolean = false
        private set

    class GroupingConfigurationBuilder internal constructor() {

        private var groupBy: String? = null
        private var groupByUnit = ""
        private var groupByRange: Any? = null
        private var groupingSortOrder = "desc"
        private var groupingListLimit = 10
        private var fullList: Boolean = false

        fun build(): GroupingConfiguration {
            if (groupBy == null) {
                throw IllegalArgumentException("Field cannot be null for a filter by parameter!")
            }

            val param = GroupingConfiguration()
            param.groupBy = groupBy
            param.groupByUnit = groupByUnit
            param.groupByRange = groupByRange
            param.groupingSortOrder = groupingSortOrder
            param.groupingListLimit = groupingListLimit
            param.isFullList = fullList

            return param
        }

        @Fluent
        fun withGroupBy(groupBy: String): GroupingConfigurationBuilder {
            this.groupBy = groupBy

            return this
        }

        @Fluent
        fun withGroupByUnit(groupByUnit: String?): GroupingConfigurationBuilder {
            this.groupByUnit = groupByUnit ?: ""

            return this
        }

        @Fluent
        fun withGroupByRange(groupByRange: Any?): GroupingConfigurationBuilder {
            this.groupByRange = groupByRange

            return this
        }

        @Fluent
        fun withGroupingSortOrder(groupingSortOrder: String?): GroupingConfigurationBuilder {
            this.groupingSortOrder = groupingSortOrder ?: "desc"

            return this
        }

        @Fluent
        fun withGroupingListLimit(groupingListLimit: Int): GroupingConfigurationBuilder {
            this.groupingListLimit = groupingListLimit

            return this
        }

        @Fluent
        fun withFullList(fullList: Boolean): GroupingConfigurationBuilder {
            this.fullList = fullList

            return this
        }

        companion object {
            private val logger = LoggerFactory.getLogger(GroupingConfigurationBuilder::class.java.simpleName)
        }
    }

    fun hasGroupRanging(): Boolean {
        return !groupByUnit!!.equals("", ignoreCase = true)
    }

    fun validate(TYPE: Class<*>, fieldName: String, validationError: JsonObject): Boolean {
        when {
            groupingListLimit == 0 -> {
                groupingListLimit = Integer.MAX_VALUE
                isFullList = true
            }
            groupingListLimit > 100 || groupingListLimit < 1 -> validationError.put("groupingListLimit",
                    groupBy + ": Must be an Integer between inclusive 1 and inclusive 100! " +
                            "If you are looking for a full list, set the size to 0!")
        }

        if (!(groupingSortOrder.equals("asc", ignoreCase = true) || groupingSortOrder.equals("desc", ignoreCase = true))) {
            validationError.put("groupSortOrder",
                    groupBy!! + ": Only ASC or DESC may be chosen for sorting order!")
        }

        try {
            val field = TYPE.getDeclaredField(fieldName)
            val fieldType = field.type

            when {
                fieldType === Long::class.java ||
                        fieldType === Int::class.java ||
                        fieldType === Double::class.java ||
                        fieldType === Float::class.java ||
                        fieldType === Short::class.java ||
                        fieldType === Long::class.javaPrimitiveType ||
                        fieldType === Int::class.javaPrimitiveType ||
                        fieldType === Double::class.javaPrimitiveType ||
                        fieldType === Float::class.javaPrimitiveType ||
                        fieldType === Short::class.javaPrimitiveType -> {
                    return when {
                        groupByUnit == null -> true
                        groupByUnit!!.equals("INTEGER", ignoreCase = true) -> true
                        else -> false
                    }
                }
                else -> return when {
                    fieldType === Date::class.java ->
                        when (groupByUnit) {
                            null -> throw IllegalArgumentException("Cannot aggregate on dates without a unit!")
                            else -> try {
                                AggregateFunction.TIMEUNIT_DATE.valueOf(groupByRange!!.toString().toUpperCase())

                                true
                            } catch (ex: IllegalArgumentException) {
                                false
                            }
                        }
                    else -> throw IllegalArgumentException("Not an aggregatable field!")
                }
            }
        } catch (iae: IllegalArgumentException) {
            validationError.put("field_error",
                    "This field is not of a type that can be aggregated with this function!")
        } catch (e: NoSuchFieldException) {
            validationError.put("field_error",
                    "The requested field does not exist on this model...")
        }

        return false
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as GroupingConfiguration?

        return groupingListLimit == that!!.groupingListLimit &&
                isFullList == that.isFullList &&
                groupBy == that.groupBy &&
                groupByUnit == that.groupByUnit &&
                groupByRange == that.groupByRange &&
                groupingSortOrder == that.groupingSortOrder
    }

    override fun hashCode(): Int {
        return Objects.hash(groupBy, groupByUnit, if (groupByRange == null) 1234L else groupByRange!!.toString(),
                groupingSortOrder, isFullList, groupingListLimit)
    }

    companion object {
        fun builder(): GroupingConfigurationBuilder {
            return GroupingConfigurationBuilder()
        }
    }
}
