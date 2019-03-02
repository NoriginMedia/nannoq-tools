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
