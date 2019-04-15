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

import com.nannoq.tools.repository.utils.AggregateFunctions.*
import com.nannoq.tools.repository.utils.CrossModelAggregateFunction
import com.nannoq.tools.repository.utils.CrossModelGroupingConfiguration
import org.junit.Assert.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrossModelAggregateFunctionTest {
    private var validAggregateFunction: CrossModelAggregateFunction? = null

    @BeforeEach
    @Throws(Exception::class)
    fun setUp() {
        validAggregateFunction = CrossModelAggregateFunction(COUNT, "viewCount")
    }

    @Test
    @Throws(Exception::class)
    fun getFunction() {
        assertEquals(COUNT, validAggregateFunction!!.function)
        assertNotEquals(MIN, validAggregateFunction!!.function)
        assertNotEquals(MAX, validAggregateFunction!!.function)
        assertNotEquals(AVG, validAggregateFunction!!.function)
        assertNotEquals(SUM, validAggregateFunction!!.function)
    }

    @Test
    @Throws(Exception::class)
    fun isMin() {
        assertFalse(validAggregateFunction!!.isMin)
        assertTrue(validAggregateFunction!!.isCount)
        assertTrue(CrossModelAggregateFunction(MIN, "viewCount").isMin)
    }

    @Test
    @Throws(Exception::class)
    fun isMax() {
        assertFalse(validAggregateFunction!!.isMax)
        assertTrue(validAggregateFunction!!.isCount)
        assertTrue(CrossModelAggregateFunction(MAX, "viewCount").isMax)
    }

    @Test
    @Throws(Exception::class)
    fun isAverage() {
        assertFalse(validAggregateFunction!!.isAverage)
        assertTrue(validAggregateFunction!!.isCount)
        assertTrue(CrossModelAggregateFunction(AVG, "viewCount").isAverage)
    }

    @Test
    @Throws(Exception::class)
    fun isSum() {
        assertFalse(validAggregateFunction!!.isSum)
        assertTrue(validAggregateFunction!!.isCount)
        assertTrue(CrossModelAggregateFunction(SUM, "viewCount").isSum)
    }

    @Test
    @Throws(Exception::class)
    fun isCount() {
        assertTrue(validAggregateFunction!!.isCount)
        assertFalse(validAggregateFunction!!.isMax)
    }

    @Test
    @Throws(Exception::class)
    fun getGroupBy() {
        assertTrue(validAggregateFunction!!.groupBy!!.isEmpty())
        assertTrue(CrossModelAggregateFunction(COUNT, "viewCount",
                listOf(CrossModelGroupingConfiguration(listOf("viewCount")))).hasGrouping())
    }

    @Test
    @Throws(Exception::class)
    fun hasGrouping() {
        assertFalse(validAggregateFunction!!.hasGrouping())
        assertTrue(CrossModelAggregateFunction(COUNT, "viewCount",
                listOf(CrossModelGroupingConfiguration(listOf("viewCount")))).hasGrouping())
    }
}