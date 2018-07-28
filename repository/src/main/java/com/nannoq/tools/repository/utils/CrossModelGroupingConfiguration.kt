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
import io.vertx.core.json.JsonObject
import java.util.*

/**
 * This class defines grouping configuration for cross-model queries.
 *
 * groupByUnit is the unit of grouping (DATE, INTEGER)
 * groupByRange is the interval in the range of grouping (10000, HOUR, DAY, MONTH, YEAR)
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class CrossModelGroupingConfiguration {
    var groupBy: List<String>? = null
        private set
    var groupByUnit: String? = null
        private set
    var groupByRange: Any? = null
        private set
    var groupingSortOrder: String? = null
        private set
    var groupingListLimit: Int = 0
        get() = if (field == 0 || isFullList) Integer.MAX_VALUE else field
    @JsonIgnore
    var isFullList: Boolean = false

    constructor() {
        this.groupBy = ArrayList()
        this.groupByUnit = ""
        this.groupByRange = null
        this.groupingSortOrder = "desc"
        this.groupingListLimit = 10
    }

    @JvmOverloads constructor(groupBy: List<String>, groupByUnit: String = "", groupByRange: Any? = null, groupingSortOrder: String = "desc", groupingListLimit: Int = 10, fullList: Boolean = false) {
        this.groupBy = groupBy
        this.groupByUnit = groupByUnit
        this.groupByRange = groupByRange
        this.groupingSortOrder = groupingSortOrder
        this.groupingListLimit = groupingListLimit
        this.isFullList = fullList
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
                    groupBy.toString() + ": Must be an Integer between inclusive 1 and inclusive 100! " +
                            "If you are looking for a full list, set the size to 0!")
        }

        if (!(groupingSortOrder!!.equals("asc", ignoreCase = true) || groupingSortOrder!!.equals("desc", ignoreCase = true))) {
            validationError.put("groupSortOrder",
                    groupBy!!.toString() + ": Only ASC or DESC may be chosen for sorting order!")
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
                        fieldType === Short::class.javaPrimitiveType -> return when {
                    groupByUnit == null -> true
                    groupByUnit!!.equals("INTEGER", ignoreCase = true) -> true
                    else -> {
                        validationError.put("field_error", "Field cannot be found!")

                        false
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
                                validationError.put("field_error", "Cannot convert value to timevalue")

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

        return validationError.isEmpty
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false

        val that = o as CrossModelGroupingConfiguration?

        return groupingListLimit == that!!.groupingListLimit && groupBy == that.groupBy &&
                (if (groupByUnit != null) groupByUnit == that.groupByUnit else that.groupByUnit == null) &&
                (if (groupByRange != null) groupByRange == that.groupByRange else that.groupByRange == null) &&
                if (groupingSortOrder != null) groupingSortOrder == that.groupingSortOrder else that.groupingSortOrder == null
    }

    override fun hashCode(): Int {
        var result = groupBy!!.hashCode()
        result = 31 * result + if (groupByUnit != null) groupByUnit!!.hashCode() else 0
        result = 31 * result + if (groupByRange != null) groupByRange!!.toString().hashCode() else 0
        result = 31 * result + if (groupingSortOrder != null) groupingSortOrder!!.hashCode() else 0
        result = 31 * result + groupingListLimit

        return result
    }
}
