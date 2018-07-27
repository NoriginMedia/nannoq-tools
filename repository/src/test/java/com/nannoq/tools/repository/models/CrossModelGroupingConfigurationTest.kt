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

import com.nannoq.tools.repository.utils.CrossModelGroupingConfiguration
import org.junit.Before
import org.junit.Test

import java.util.Collections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class CrossModelGroupingConfigurationTest {
    private var validGroupingConfiguration: CrossModelGroupingConfiguration? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        validGroupingConfiguration = CrossModelGroupingConfiguration(listOf<String>("releaseDate"))
    }

    @Test
    @Throws(Exception::class)
    fun isFullList() {
        assertFalse(validGroupingConfiguration!!.isFullList)
        validGroupingConfiguration!!.isFullList = true
        assertTrue(validGroupingConfiguration!!.isFullList)
    }

    @Test
    @Throws(Exception::class)
    fun hasGroupRanging() {
        assertFalse(validGroupingConfiguration!!.hasGroupRanging())
        validGroupingConfiguration = CrossModelGroupingConfiguration(
                listOf<String>("releaseDate"), "DATE", "WEEK")
        assertTrue(validGroupingConfiguration!!.hasGroupRanging())
    }
}