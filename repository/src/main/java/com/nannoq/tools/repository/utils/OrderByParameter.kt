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

import io.vertx.codegen.annotations.Fluent
import io.vertx.core.logging.LoggerFactory
import java.util.*

/**
 * This class defines an orderByParameter, which is used for sorting results.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class OrderByParameter {
    var field: String? = null
    var direction: String? = null

    val isAsc: Boolean
        get() = direction != null && direction!!.equals("asc", ignoreCase = true)

    val isDesc: Boolean
        get() = direction == null || direction!!.equals("desc", ignoreCase = true)

    val isValid: Boolean
        get() = this.field != null && (isAsc && !isDesc || isDesc && !isAsc)

    class OrderByParameterBuilder internal constructor() {
        private var field: String? = null
        private var direction: String? = null

        fun build(): OrderByParameter {
            if (field == null) {
                throw IllegalArgumentException("Field cannot be null for an order by parameter!")
            }

            val param = OrderByParameter()
            param.field = field

            when {
                direction != null -> param.direction = direction
                else -> param.direction = "desc"
            }

            return param
        }

        @Fluent
        fun withField(field: String): OrderByParameter.OrderByParameterBuilder {
            this.field = field
            return this
        }

        @Fluent
        fun withDirection(direction: String): OrderByParameter.OrderByParameterBuilder {
            this.direction = direction

            return this
        }

        companion object {
            private val logger = LoggerFactory.getLogger(FilterParameter.FilterParameterBuilder::class.java.simpleName)
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as OrderByParameter?

        return field == that!!.field && direction == that.direction
    }

    override fun hashCode(): Int {
        return Objects.hash(field, direction)
    }

    companion object {
        fun builder(): OrderByParameter.OrderByParameterBuilder {
            return OrderByParameter.OrderByParameterBuilder()
        }
    }
}
