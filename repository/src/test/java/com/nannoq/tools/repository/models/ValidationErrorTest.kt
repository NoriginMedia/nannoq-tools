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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

class ValidationErrorTest {
    private var validationError: ValidationError? = null

    @BeforeEach
    @Throws(Exception::class)
    fun setUp() {
        validationError = ValidationError("Cannot be null!", "viewCount")
    }

    @Test
    @Throws(Exception::class)
    fun toJson() {
        val jsonObject = validationError!!.toJson()

        assertEquals("Cannot be null!", jsonObject.getString("description"))
        assertEquals("viewCount", jsonObject.getString("fieldName"))
    }

    @Test
    @Throws(Exception::class)
    fun validateNotNull() {
        assertNotNull(ValidationError.validateNotNull(null, "field"))
        assertNull(ValidationError.validateNotNull("lol", "field"))
    }

    @Test
    @Throws(Exception::class)
    fun validateDate() {
        assertNotNull(ValidationError.validateDate(null, "field"))
        assertNotNull(ValidationError.validateDate(Date(1), "field"))
        assertNull(ValidationError.validateDate(Date(), "field"))
    }

    @Test
    @Throws(Exception::class)
    fun validateTextLength() {
        assertNotNull(ValidationError.validateTextLength(null, "field", 5))
        assertNotNull(ValidationError.validateTextLength("lol", "field", 2))
        assertNull(ValidationError.validateTextLength("lol", "field", 3))
    }
}