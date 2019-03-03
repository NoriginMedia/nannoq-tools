package com.nannoq.tools.version.mocks

import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

class MockVersionObject {

    var stringOne: String? = null
    var shortOne: Short? = null
    var shortTwo: Short? = 0
    var integerOne: Int? = null
    var integerTwo: Int = 0
    var longOne: Long? = null
    var longTwo: Long? = 0
    var doubleOne: Double? = null
    var doubleTwo: Double? = 0.toDouble()
    var floatOne: Float? = null
    var floatTwo: Float = 0.toFloat()
    var booleanOne: Boolean? = null
    var booleanTwo: Boolean = false
    var mockEnumObject: MockEnumObject? = null
    var bigIntegerOne: BigInteger? = null
    var bigDecimalOne: BigDecimal? = null
    var dateOne: Date? = null
    var mockVersionObject: MockVersionObject? = null
    var listObjects: List<MockVersionListObject>? = null
    var setObjects: Set<MockVersionListObject>? = null
    var mapSimpleObjects: Map<String, Int>? = null
    var mapComplexObjects: Map<String, MockVersionObject>? = null

    fun withListObjects(listObjectsBefore: ArrayList<MockVersionListObject>): MockVersionObject {
        listObjects = listObjectsBefore

        return this
    }

    fun withSetObjects(objectsBefore: LinkedHashSet<MockVersionListObject>): MockVersionObject {
        setObjects = objectsBefore

        return this
    }

    fun withMapSimpleObjects(mapSimpleObjectsBefore: LinkedHashMap<String, Int>): MockVersionObject {
        mapSimpleObjects = mapSimpleObjectsBefore

        return this
    }

    fun withMapComplexObjects(mapComplexObjectsBefore: LinkedHashMap<String, MockVersionObject>): MockVersionObject {
        mapComplexObjects = mapComplexObjectsBefore

        return this
    }

    fun withStringOne(s: String): MockVersionObject {
        stringOne = s

        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MockVersionObject

        if (stringOne != other.stringOne) return false
        if (shortOne != other.shortOne) return false
        if (shortTwo != other.shortTwo) return false
        if (integerOne != other.integerOne) return false
        if (integerTwo != other.integerTwo) return false
        if (longOne != other.longOne) return false
        if (longTwo != other.longTwo) return false
        if (doubleOne != other.doubleOne) return false
        if (doubleTwo != other.doubleTwo) return false
        if (floatOne != other.floatOne) return false
        if (floatTwo != other.floatTwo) return false
        if (booleanOne != other.booleanOne) return false
        if (booleanTwo != other.booleanTwo) return false
        if (mockEnumObject != other.mockEnumObject) return false
        if (bigIntegerOne != other.bigIntegerOne) return false
        if (bigDecimalOne != other.bigDecimalOne) return false
        if (dateOne != other.dateOne) return false
        if (mockVersionObject != other.mockVersionObject) return false
        if (listObjects != other.listObjects) return false
        if (setObjects != other.setObjects) return false
        if (mapSimpleObjects != other.mapSimpleObjects) return false
        if (mapComplexObjects != other.mapComplexObjects) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stringOne?.hashCode() ?: 0
        result = 31 * result + (shortOne ?: 0)
        result = 31 * result + (shortTwo ?: 0)
        result = 31 * result + (integerOne ?: 0)
        result = 31 * result + integerTwo
        result = 31 * result + (longOne?.hashCode() ?: 0)
        result = 31 * result + longTwo.hashCode()
        result = 31 * result + (doubleOne?.hashCode() ?: 0)
        result = 31 * result + doubleTwo.hashCode()
        result = 31 * result + (floatOne?.hashCode() ?: 0)
        result = 31 * result + floatTwo.hashCode()
        result = 31 * result + (booleanOne?.hashCode() ?: 0)
        result = 31 * result + booleanTwo.hashCode()
        result = 31 * result + (mockEnumObject?.hashCode() ?: 0)
        result = 31 * result + (bigIntegerOne?.hashCode() ?: 0)
        result = 31 * result + (bigDecimalOne?.hashCode() ?: 0)
        result = 31 * result + (dateOne?.hashCode() ?: 0)
        result = 31 * result + (mockVersionObject?.hashCode() ?: 0)
        result = 31 * result + (listObjects?.hashCode() ?: 0)
        result = 31 * result + (setObjects?.hashCode() ?: 0)
        result = 31 * result + (mapSimpleObjects?.hashCode() ?: 0)
        result = 31 * result + (mapComplexObjects?.hashCode() ?: 0)
        return result
    }
}
