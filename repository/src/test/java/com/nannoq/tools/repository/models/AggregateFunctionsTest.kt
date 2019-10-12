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

import com.nannoq.tools.repository.utils.AggregateFunctions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Test

class AggregateFunctionsTest {
    @Test
    @Throws(Exception::class)
    fun forValue() {
        assertEquals(AggregateFunctions.MIN, AggregateFunctions.forValue("min"))
        assertEquals(AggregateFunctions.MIN, AggregateFunctions.forValue("MIN"))
        assertEquals(AggregateFunctions.MAX, AggregateFunctions.forValue("max"))
        assertEquals(AggregateFunctions.MAX, AggregateFunctions.forValue("MAX"))
        assertEquals(AggregateFunctions.AVG, AggregateFunctions.forValue("avg"))
        assertEquals(AggregateFunctions.AVG, AggregateFunctions.forValue("AVG"))
        assertEquals(AggregateFunctions.SUM, AggregateFunctions.forValue("sum"))
        assertEquals(AggregateFunctions.SUM, AggregateFunctions.forValue("SUM"))
        assertEquals(AggregateFunctions.COUNT, AggregateFunctions.forValue("count"))
        assertEquals(AggregateFunctions.COUNT, AggregateFunctions.forValue("COUNT"))
        assertNull(AggregateFunctions.forValue("bogus"))
    }

    @Test
    @Throws(Exception::class)
    fun toValue() {
        assertEquals("MIN", AggregateFunctions.MIN.toValue())
        assertEquals("MAX", AggregateFunctions.MAX.toValue())
        assertEquals("AVG", AggregateFunctions.AVG.toValue())
        assertEquals("SUM", AggregateFunctions.SUM.toValue())
        assertEquals("COUNT", AggregateFunctions.COUNT.toValue())
    }
}
