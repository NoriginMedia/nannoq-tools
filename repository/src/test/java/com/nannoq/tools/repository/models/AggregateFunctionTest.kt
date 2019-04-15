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

import com.nannoq.tools.repository.utils.AggregateFunction
import com.nannoq.tools.repository.utils.AggregateFunctions.*
import com.nannoq.tools.repository.utils.GroupingConfiguration
import org.junit.Assert.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AggregateFunctionTest {
    private var validAggregateFunction: AggregateFunction? = null

    @BeforeEach
    @Throws(Exception::class)
    fun setUp() {
        validAggregateFunction = AggregateFunction.builder()
                .withAggregateFunction(COUNT)
                .withField("viewCount")
                .build()
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
        assertTrue(AggregateFunction.builder()
                .withAggregateFunction(MIN)
                .withField("viewCount")
                .build().isMin)
    }

    @Test
    @Throws(Exception::class)
    fun isMax() {
        assertFalse(validAggregateFunction!!.isMax)
        assertTrue(validAggregateFunction!!.isCount)
        assertTrue(AggregateFunction.builder()
                .withAggregateFunction(MAX)
                .withField("viewCount")
                .build().isMax)
    }

    @Test
    @Throws(Exception::class)
    fun isAverage() {
        assertFalse(validAggregateFunction!!.isAverage)
        assertTrue(validAggregateFunction!!.isCount)
        assertTrue(AggregateFunction.builder()
                .withAggregateFunction(AVG)
                .withField("viewCount")
                .build().isAverage)
    }

    @Test
    @Throws(Exception::class)
    fun isSum() {
        assertFalse(validAggregateFunction!!.isSum)
        assertTrue(validAggregateFunction!!.isCount)
        assertTrue(AggregateFunction.builder()
                .withAggregateFunction(SUM)
                .withField("viewCount")
                .build().isSum)
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
        assertFalse(AggregateFunction.builder()
                .withAggregateFunction(COUNT)
                .withField("viewCount")
                .withGroupBy(listOf(GroupingConfiguration.builder().withGroupBy("viewCount").build()))
                .build().groupBy!!.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun hasGrouping() {
        assertFalse(validAggregateFunction!!.hasGrouping())
        assertTrue(AggregateFunction.builder()
                .withAggregateFunction(COUNT)
                .withField("viewCount")
                .withGroupBy(listOf(GroupingConfiguration.builder().withGroupBy("viewCount").build()))
                .build().hasGrouping())
    }
}