/*
 * MIT License
 *
 * Copyright (c) 2019 Anders Mikkelsen
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
 */

package com.nannoq.tools.version.mocks

import com.nannoq.tools.version.models.IteratorId

class MockVersionListObject {

    @IteratorId
    var iteratorId: Int? = null
    var mockEnumObject: MockEnumObject? = null
    var stringOne: String? = null
    var mockVersionObject: MockVersionObject? = null
    var subListObjects: List<MockVersionListObject>? = null

    fun withSubListObjects(listSubObjects: List<MockVersionListObject>): MockVersionListObject {
        subListObjects = listSubObjects

        return this
    }

    fun withStringOne(s: String): MockVersionListObject {
        stringOne = s

        return this
    }

    fun withIteratorId(i: Int?): MockVersionListObject {
        iteratorId = i

        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MockVersionListObject

        if (iteratorId != other.iteratorId) return false
        if (mockEnumObject != other.mockEnumObject) return false
        if (stringOne != other.stringOne) return false
        if (mockVersionObject != other.mockVersionObject) return false
        if (subListObjects != other.subListObjects) return false

        return true
    }

    override fun hashCode(): Int {
        var result = iteratorId ?: 0
        result = 31 * result + (mockEnumObject?.hashCode() ?: 0)
        result = 31 * result + (stringOne?.hashCode() ?: 0)
        result = 31 * result + (mockVersionObject?.hashCode() ?: 0)
        result = 31 * result + (subListObjects?.hashCode() ?: 0)
        return result
    }
}
