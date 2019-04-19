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

import com.nannoq.tools.repository.utils.FilterParameter.FILTER_TYPE.valueOf
import com.nannoq.tools.repository.utils.FilterParameter.FILTER_TYPE.AND
import com.nannoq.tools.repository.utils.FilterParameter.FILTER_TYPE.OR
import io.vertx.codegen.annotations.Fluent
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.util.Arrays
import java.util.Objects

/**
 * This class defines the operation to be performed on a specific field, with OR and AND types included.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class FilterParameter {
    var field: String? = null
    var eq: Any? = null
        private set
    var ne: Any? = null
        private set
    var gt: Any? = null
        private set
    var ge: Any? = null
        private set
    var lt: Any? = null
        private set
    var le: Any? = null
        private set
    var contains: Any? = null
        private set
    var notContains: Any? = null
        private set
    var beginsWith: Any? = null
        private set
    var `in`: Array<Any>? = null
        private set
    var type: String? = null
        get() {
            if (field == null) {
                return "AND"
            }

            return if (field!!.equals("AND", ignoreCase = true) || field!!.equals("OR", ignoreCase = true)) {
                field!!.toUpperCase()
            } else null
        }

    val isEq: Boolean
        get() = eq != null && ne == null && gt == null && lt == null && ge == null && le == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isNe: Boolean
        get() = eq == null && ne != null && gt == null && lt == null && ge == null && le == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isIn: Boolean
        get() = `in` != null && eq == null && ne == null && gt == null && lt == null && ge == null && le == null && contains == null && notContains == null && beginsWith == null

    val isGt: Boolean
        get() = gt != null && lt == null && ge == null && le == null && eq == null && ne == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isLt: Boolean
        get() = lt != null && gt == null && ge == null && le == null && eq == null && ne == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isGe: Boolean
        get() = ge != null && le == null && gt == null && lt == null && eq == null && ne == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isLe: Boolean
        get() = le != null && ge == null && gt == null && lt == null && eq == null && ne == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isBetween: Boolean
        get() = gt != null && lt != null && ge == null && le == null && eq == null && ne == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isInclusiveBetween: Boolean
        get() = ge != null && le != null && gt == null && lt == null && eq == null && ne == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isGeLtVariableBetween: Boolean
        get() = ge != null && lt != null && gt == null && le == null && eq == null && ne == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isLeGtVariableBetween: Boolean
        get() = le != null && gt != null && ge == null && lt == null && eq == null && ne == null && contains == null && notContains == null && beginsWith == null && `in` == null

    val isContains: Boolean
        get() = contains != null && le == null && ge == null && gt == null && lt == null && eq == null && ne == null && notContains == null && beginsWith == null && `in` == null

    val isNotContains: Boolean
        get() = notContains != null && le == null && ge == null && gt == null && lt == null && eq == null && ne == null && contains == null && beginsWith == null && `in` == null

    val isBeginsWith: Boolean
        get() = beginsWith != null && le == null && ge == null && gt == null && lt == null && eq == null && ne == null && contains == null && notContains == null && `in` == null

    val isIllegalRangedKeyParam: Boolean
        get() = isContains || isNotContains || isIn

    val isValid: Boolean
        get() {
            val logger = LoggerFactory.getLogger(FilterParameter::class.java)
            logger.debug("EQ: $isEq")
            logger.debug("NE: $isNe")
            logger.debug("IN: $isIn")
            logger.debug("GT: $isGt")
            logger.debug("LT: $isLt")
            logger.debug("GE: $isGe")
            logger.debug("LE: $isLe")
            logger.debug("BETWEEN: $isBetween")
            logger.debug("INCLUSIVE_BETWEEN: $isInclusiveBetween")
            logger.debug("GE_LT_VARIABLE_BETWEEN: $isGeLtVariableBetween")
            logger.debug("LE_GT_VARIABLE_BETWEEN: $isLeGtVariableBetween")
            logger.debug("CONTAINS: $isContains")
            logger.debug("NOT_CONTAINS: $isNotContains")
            logger.debug("BEGINS_WITH: $isBeginsWith")

            return this.field != null && type != null &&
                    (isEq || isNe || isGt || isLt || isGe || isLe ||
                            isBetween || isInclusiveBetween || isGeLtVariableBetween || isLeGtVariableBetween ||
                            isContains || isNotContains || isBeginsWith || isIn)
        }

    enum class FILTER_TYPE {
        AND, OR
    }

    class FilterParameterBuilder {

        private var field: String? = null

        private var eq: Any? = null
        private var ne: Any? = null
        private var gt: Any? = null
        private var ge: Any? = null
        private var lt: Any? = null
        private var le: Any? = null
        private var contains: Any? = null
        private var notContains: Any? = null
        private var beginsWith: Any? = null
        private var `in`: Array<Any>? = null
        private var type: String? = null

        private var fieldSet = false
        private var operatorSet = false
        private var typeSet = false

        internal constructor()

        internal constructor(field: String) {
            withField(field)
        }

        fun build(): FilterParameter {
            if (field == null) {
                throw IllegalArgumentException("Field cannot be null for a filter by parameter!")
            }

            val param = FilterParameter()
            param.field = field
            param.eq = eq
            param.ne = ne
            param.gt = gt
            param.ge = ge
            param.lt = lt
            param.le = le
            param.contains = contains
            param.notContains = notContains
            param.beginsWith = beginsWith
            param.`in` = `in`
            param.type = type

            if (!param.isValid) {
                val errors = JsonObject()
                param.collectErrors(errors)

                throw IllegalArgumentException("This parameter is invalid!" + errors.encodePrettily())
            }

            return param
        }

        @Fluent
        fun withField(field: String): FilterParameterBuilder {
            if (fieldSet) {
                throw IllegalArgumentException("Field cannot be replaced after being initially set!")
            }

            fieldSet = true
            this.field = field

            return this
        }

        @Fluent
        fun withEq(eq: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.eq = eq

            return this
        }

        @Fluent
        fun withNe(ne: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.ne = ne

            return this
        }

        @Fluent
        fun withGt(gt: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.gt = gt

            return this
        }

        @Fluent
        fun withGe(ge: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.ge = ge

            return this
        }

        @Fluent
        fun withLt(lt: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.lt = lt

            return this
        }

        @Fluent
        fun withLe(le: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.le = le

            return this
        }

        @Fluent
        fun withBetween(gt: Any, lt: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.gt = gt
            this.lt = lt

            return this
        }

        @Fluent
        fun withInclusiveBetween(ge: Any, le: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.ge = ge
            this.le = le

            return this
        }

        @Fluent
        fun withGeLtVariableBetween(ge: Any, lt: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.ge = ge
            this.lt = lt

            return this
        }

        @Fluent
        fun withLeGtVariableBetween(gt: Any, le: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.le = le
            this.gt = gt

            return this
        }

        @Fluent
        fun withContains(contains: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.contains = contains

            return this
        }

        @Fluent
        fun withNotContains(notContains: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.notContains = notContains

            return this
        }

        @Fluent
        fun withBeginsWith(beginsWith: Any): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.beginsWith = beginsWith

            return this
        }

        @Fluent
        fun withIn(`in`: Array<Any>): FilterParameterBuilder {
            if (operatorSet) {
                throw IllegalArgumentException("Operator cannot be replaced after being initially set!")
            }

            operatorSet = true
            this.`in` = `in`

            return this
        }

        @Fluent
        fun withType(type: FILTER_TYPE): FilterParameterBuilder {
            if (typeSet) {
                throw IllegalArgumentException("Type cannot be replaced after being initially set!")
            }

            typeSet = true

            when (type) {
                AND -> this.type = "and"
                OR -> this.type = "or"
            }

            return this
        }

        companion object {
            private val logger = LoggerFactory.getLogger(FilterParameterBuilder::class.java.simpleName)
        }
    }

    @Fluent
    fun setField(field: String): FilterParameter {
        this.field = field

        return this
    }

    @Fluent
    fun setType(type: String): FilterParameter {
        when (valueOf(type.toUpperCase())) {
            AND -> this.type = "AND"
            OR -> this.type = "OR"
        }

        return this
    }

    fun collectErrors(errors: JsonObject) {
        when {
            (ne != null || eq != null) && (gt != null || ge != null || lt != null || le != null) ->
                errors.put(field!! + "_error", "Filter Parameter error on: '" + field + "', " +
                    "'eq' or 'ne' cannot co exist with 'gt','lt','ge' or 'le' parameters!")
            else -> errors.put(field!! + "_error", "Advanced functions cannot be used in conjunction with simple functions.")
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as FilterParameter?

        return field == that!!.field &&
                eq == that.eq &&
                ne == that.ne &&
                gt == that.gt &&
                ge == that.ge &&
                lt == that.lt &&
                le == that.le &&
                contains == that.contains &&
                notContains == that.notContains &&
                beginsWith == that.beginsWith &&
                Arrays.equals(`in`, that.`in`) &&
                type == that.type
    }

    override fun hashCode(): Int {
        var result = Objects.hash(field,
                if (eq == null) 1234L else eq!!.toString(),
                if (ne == null) 1234L else ne!!.toString(),
                if (gt == null) 1234L else gt!!.toString(),
                if (ge == null) 1234L else ge!!.toString(),
                if (lt == null) 1234L else lt!!.toString(),
                if (le == null) 1234L else le!!.toString(),
                if (contains == null) 1234L else contains!!.toString(),
                if (notContains == null) 1234L else notContains!!.toString(),
                if (beginsWith == null) 1234L else beginsWith!!.toString(), type)
        result = 31 * result + Arrays.hashCode(`in`)

        return result
    }

    companion object {
        fun builder(): FilterParameterBuilder {
            return FilterParameterBuilder()
        }

        fun builder(field: String): FilterParameterBuilder {
            return FilterParameterBuilder(field)
        }
    }
}
