package com.nannoq.tools.version

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.nannoq.tools.version.manager.VersionManager
import com.nannoq.tools.version.mocks.MockEnumObject
import com.nannoq.tools.version.mocks.MockVersionListObject
import com.nannoq.tools.version.mocks.MockVersionObject
import com.nannoq.tools.version.models.DiffPair
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.stream.Collectors.toList
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.fail

@RunWith(VertxUnitRunner::class)
class VersionManagerTest {
    private val versionManager = VersionManager()

    private val listObjectSupplier = {
        val mockVersionListObject = MockVersionListObject()
        
        mockVersionListObject.stringOne = "testList"
        
        mockVersionListObject
    }

    @Test
    fun setIteratorIds_returnsObjectWithIteratorIdsSet() {
        val iteratorBeforeObjects = IntStream.range(0, 100)
                .mapToObj { this.newIteratorBeforeObject(it) }
                .collect(toList())

        val versionObjects = versionManager.setIteratorIds(iteratorBeforeObjects)

        val iteratorAfterObjects = IntStream.range(0, 100)
                .mapToObj { this.newIteratorAfterObject(it) }
                .collect(toList())

        versionObjects.forEach { versionObject ->
            try {
                assertEquals(ObjectMapper().writeValueAsString(iteratorAfterObjects[versionObject.integerOne!!]),
                        ObjectMapper().writeValueAsString(versionObject))
            } catch (e: JsonProcessingException) {
                fail(e.message)
            }
        }
    }

    private fun newIteratorBeforeObject(i: Int): MockVersionObject {
        val listObjects = ArrayList<MockVersionListObject>()
        listObjects.add(listObjectSupplier().withSubListObjects(listSubObjects()))
        listObjects.add(listObjectSupplier().withSubListObjects(listSubObjects()))
        listObjects.add(listObjectSupplier().withSubListObjects(listSubObjects()))
        val setObjects = LinkedHashSet<MockVersionListObject>()
        setObjects.add(listObjectSupplier().withStringOne("testOne").withSubListObjects(listSubObjects()))
        setObjects.add(listObjectSupplier().withStringOne("testTwo").withSubListObjects(listSubObjects()))
        setObjects.add(listObjectSupplier().withStringOne("testThree").withSubListObjects(listSubObjects()))

        val mockVersionObject = MockVersionObject()
        mockVersionObject.stringOne = "test"
        mockVersionObject.integerOne = i
        mockVersionObject.listObjects = listObjects
        mockVersionObject.setObjects = setObjects
        mockVersionObject.mockVersionObject = newSubIteratorBeforeObject(i)

        return mockVersionObject
    }

    private fun newSubIteratorBeforeObject(i: Int): MockVersionObject {
        val listObjects = ArrayList<MockVersionListObject>()
        listObjects.add(listObjectSupplier().withSubListObjects(listSubObjects()))
        listObjects.add(listObjectSupplier().withSubListObjects(listSubObjects()))
        listObjects.add(listObjectSupplier().withSubListObjects(listSubObjects()))
        val setObjects = LinkedHashSet<MockVersionListObject>()
        setObjects.add(listObjectSupplier().withStringOne("testOne").withSubListObjects(listSubObjects()))
        setObjects.add(listObjectSupplier().withStringOne("testTwo").withSubListObjects(listSubObjects()))
        setObjects.add(listObjectSupplier().withStringOne("testThree").withSubListObjects(listSubObjects()))

        val mockVersionObject = MockVersionObject()
        mockVersionObject.stringOne = "test"
        mockVersionObject.integerOne = i
        mockVersionObject.listObjects = listObjects
        mockVersionObject.setObjects = setObjects

        return mockVersionObject
    }

    private fun newIteratorAfterObject(i: Int): MockVersionObject {
        val listFinalObjects = ArrayList<MockVersionListObject>()
        listFinalObjects.add(listObjectSupplier().withIteratorId(0).withSubListObjects(listFinalSubObjects()))
        listFinalObjects.add(listObjectSupplier().withIteratorId(1).withSubListObjects(listFinalSubObjects()))
        listFinalObjects.add(listObjectSupplier().withIteratorId(2).withSubListObjects(listFinalSubObjects()))
        val setFinalObjects = LinkedHashSet<MockVersionListObject>()
        setFinalObjects.add(listObjectSupplier().withIteratorId(0).withStringOne("testOne")
                .withSubListObjects(listFinalSubObjects()))
        setFinalObjects.add(listObjectSupplier().withIteratorId(1).withStringOne("testTwo")
                .withSubListObjects(listFinalSubObjects()))
        setFinalObjects.add(listObjectSupplier().withIteratorId(2).withStringOne("testThree")
                .withSubListObjects(listFinalSubObjects()))

        val mockVersionObject = MockVersionObject()
        mockVersionObject.stringOne = "test"
        mockVersionObject.integerOne = i
        mockVersionObject.listObjects = listFinalObjects
        mockVersionObject.setObjects = setFinalObjects
        mockVersionObject.mockVersionObject = newSubIteratorAfterObject(i)

        return mockVersionObject
    }

    private fun newSubIteratorAfterObject(i: Int): MockVersionObject {
        val listFinalObjects = ArrayList<MockVersionListObject>()
        listFinalObjects.add(listObjectSupplier().withIteratorId(0).withSubListObjects(listFinalSubObjects()))
        listFinalObjects.add(listObjectSupplier().withIteratorId(1).withSubListObjects(listFinalSubObjects()))
        listFinalObjects.add(listObjectSupplier().withIteratorId(2).withSubListObjects(listFinalSubObjects()))
        val setFinalObjects = LinkedHashSet<MockVersionListObject>()
        setFinalObjects.add(listObjectSupplier().withIteratorId(0).withStringOne("testOne")
                .withSubListObjects(listFinalSubObjects()))
        setFinalObjects.add(listObjectSupplier().withIteratorId(1).withStringOne("testTwo")
                .withSubListObjects(listFinalSubObjects()))
        setFinalObjects.add(listObjectSupplier().withIteratorId(2).withStringOne("testThree")
                .withSubListObjects(listFinalSubObjects()))

        val mockVersionObject = MockVersionObject()
        mockVersionObject.stringOne = "test"
        mockVersionObject.integerOne = i
        mockVersionObject.listObjects = listFinalObjects
        mockVersionObject.setObjects = setFinalObjects

        return mockVersionObject
    }

    private fun listSubObjects(): List<MockVersionListObject> {
        val listSubObjects = ArrayList<MockVersionListObject>()
        listSubObjects.add(listObjectSupplier())
        listSubObjects.add(listObjectSupplier())
        listSubObjects.add(listObjectSupplier())

        return listSubObjects
    }

    private fun listFinalSubObjects(): List<MockVersionListObject> {
        val listFinalSubObjects = ArrayList<MockVersionListObject>()
        listFinalSubObjects.add(listObjectSupplier().withIteratorId(0))
        listFinalSubObjects.add(listObjectSupplier().withIteratorId(1))
        listFinalSubObjects.add(listObjectSupplier().withIteratorId(2))

        return listFinalSubObjects
    }

    @Test
    fun multipleListModifications_areMappedCorrectly() {
        val listObjectsBefore = ArrayList<MockVersionListObject>()
        listObjectsBefore.add(listObjectSupplier().withIteratorId(0))
        listObjectsBefore.add(listObjectSupplier().withIteratorId(1))
        listObjectsBefore.add(listObjectSupplier().withIteratorId(2))
        listObjectsBefore.add(listObjectSupplier().withIteratorId(3))
        val setObjectsBefore = LinkedHashSet<MockVersionListObject>()
        setObjectsBefore.add(listObjectSupplier().withStringOne("testOne").withIteratorId(0))
        setObjectsBefore.add(listObjectSupplier().withStringOne("testTwo").withIteratorId(1))
        setObjectsBefore.add(listObjectSupplier().withStringOne("testThree").withIteratorId(2))
        setObjectsBefore.add(listObjectSupplier().withStringOne("testFour").withIteratorId(3))
        val mapSimpleObjectsBefore = LinkedHashMap<String, Int>()
        mapSimpleObjectsBefore["one"] = 1
        mapSimpleObjectsBefore["two"] = 2
        mapSimpleObjectsBefore["three"] = 3
        mapSimpleObjectsBefore["four"] = 4
        mapSimpleObjectsBefore["five"] = 5
        val mapComplexObjectsBefore = LinkedHashMap<String, MockVersionObject>()
        mapComplexObjectsBefore["one"] = newBeforeSimpleFields()
        mapComplexObjectsBefore["two"] = newBeforeSimpleFields()
        mapComplexObjectsBefore["three"] = newBeforeSimpleFields()
        mapComplexObjectsBefore["four"] = newBeforeSimpleFields()
        mapComplexObjectsBefore["five"] = newBeforeSimpleFields()
        val before = newBeforeSimpleFields()
                .withListObjects(listObjectsBefore)
                .withSetObjects(setObjectsBefore)
                .withMapSimpleObjects(mapSimpleObjectsBefore)
                .withMapComplexObjects(mapComplexObjectsBefore)

        val listObjectsAfter = ArrayList<MockVersionListObject>()
        listObjectsAfter.add(listObjectSupplier().withIteratorId(0).withStringOne("testOne")
                .withSubListObjects(
                        listOf(listObjectSupplier().withStringOne("testOne").withIteratorId(null))))
        listObjectsAfter.add(listObjectSupplier().withIteratorId(null))
        listObjectsAfter.add(listObjectSupplier().withIteratorId(2).withStringOne("testTwo"))
        listObjectsAfter.add(listObjectSupplier().withIteratorId(null).withStringOne("testXOne"))
        listObjectsAfter.add(listObjectSupplier().withIteratorId(null).withStringOne("testXTwo"))
        listObjectsAfter.add(listObjectSupplier().withIteratorId(null).withStringOne("testXThree"))
        val setObjectsAfter = LinkedHashSet<MockVersionListObject>()
        setObjectsAfter.add(listObjectSupplier().withIteratorId(0).withStringOne("testTestOne"))
        setObjectsAfter.add(listObjectSupplier().withStringOne("testMiddleCreate").withIteratorId(null))
        setObjectsAfter.add(listObjectSupplier().withIteratorId(2).withStringOne("testTestTwo"))
        setObjectsAfter.add(listObjectSupplier().withIteratorId(null).withStringOne("testXOne"))
        setObjectsAfter.add(listObjectSupplier().withIteratorId(null).withStringOne("testXTwo"))
        setObjectsAfter.add(listObjectSupplier().withIteratorId(null).withStringOne("testXThree"))
        val mapSimpleObjectsAfter = LinkedHashMap<String, Int>()
        mapSimpleObjectsAfter["one"] = 2
        mapSimpleObjectsAfter["nine"] = 10
        mapSimpleObjectsAfter["four"] = 4
        mapSimpleObjectsAfter["five"] = 6
        val mapComplexObjectsAfter = LinkedHashMap<String, MockVersionObject>()
        mapComplexObjectsAfter["one"] = newBeforeSimpleFields().withStringOne("testThree")
        mapComplexObjectsAfter["nine"] = newBeforeSimpleFields().withStringOne("testTen")
        mapComplexObjectsAfter["four"] = newBeforeSimpleFields().withStringOne("testFive")
        mapComplexObjectsAfter["five"] = newBeforeSimpleFields()
        val after = newAfterSimpleFields()
                .withListObjects(listObjectsAfter)
                .withSetObjects(setObjectsAfter)
                .withMapSimpleObjects(mapSimpleObjectsAfter)
                .withMapComplexObjects(mapComplexObjectsAfter)

        val version = versionManager.extractVersion(DiffPair(current = before, updated = after))

        val newListObjectsBefore = ArrayList<MockVersionListObject>()
        newListObjectsBefore.add(listObjectSupplier())
        newListObjectsBefore.add(listObjectSupplier())
        newListObjectsBefore.add(listObjectSupplier())
        newListObjectsBefore.add(listObjectSupplier())
        val newSetObjectsBefore = LinkedHashSet<MockVersionListObject>()
        newSetObjectsBefore.add(listObjectSupplier().withStringOne("testOne"))
        newSetObjectsBefore.add(listObjectSupplier().withStringOne("testTwo"))
        newSetObjectsBefore.add(listObjectSupplier().withStringOne("testThree"))
        newSetObjectsBefore.add(listObjectSupplier().withStringOne("testFour"))
        val newMapSimpleObjectsBefore = LinkedHashMap<String, Int>()
        newMapSimpleObjectsBefore["one"] = 1
        newMapSimpleObjectsBefore["two"] = 2
        newMapSimpleObjectsBefore["three"] = 3
        newMapSimpleObjectsBefore["four"] = 4
        newMapSimpleObjectsBefore["five"] = 5
        val newMapComplexObjectsBefore = LinkedHashMap<String, MockVersionObject>()
        newMapComplexObjectsBefore["one"] = newBeforeSimpleFields()
        newMapComplexObjectsBefore["two"] = newBeforeSimpleFields()
        newMapComplexObjectsBefore["three"] = newBeforeSimpleFields()
        newMapComplexObjectsBefore["four"] = newBeforeSimpleFields()
        newMapComplexObjectsBefore["five"] = newBeforeSimpleFields()

        val newBefore = newBeforeSimpleFields()
                .withListObjects(newListObjectsBefore)
                .withSetObjects(newSetObjectsBefore)
                .withMapSimpleObjects(newMapSimpleObjectsBefore)
                .withMapComplexObjects(newMapComplexObjectsBefore)

        val newListObjectsAfter = ArrayList<MockVersionListObject>()
        newListObjectsAfter.add(listObjectSupplier().withStringOne("testOne")
                .withSubListObjects(
                        listOf(listObjectSupplier().withStringOne("testOne").withIteratorId(null))))
        newListObjectsAfter.add(listObjectSupplier())
        newListObjectsAfter.add(listObjectSupplier().withStringOne("testTwo"))
        newListObjectsAfter.add(listObjectSupplier().withStringOne("testXOne"))
        newListObjectsAfter.add(listObjectSupplier().withStringOne("testXTwo"))
        newListObjectsAfter.add(listObjectSupplier().withStringOne("testXThree"))
        val newSetObjectsAfter = LinkedHashSet<MockVersionListObject>()
        newSetObjectsAfter.add(listObjectSupplier().withStringOne("testTestOne"))
        newSetObjectsAfter.add(listObjectSupplier().withStringOne("testMiddleCreate"))
        newSetObjectsAfter.add(listObjectSupplier().withStringOne("testTestTwo"))
        newSetObjectsAfter.add(listObjectSupplier().withStringOne("testXOne"))
        newSetObjectsAfter.add(listObjectSupplier().withStringOne("testXTwo"))
        newSetObjectsAfter.add(listObjectSupplier().withStringOne("testXThree"))
        val newMapSimpleObjectsAfter = LinkedHashMap<String, Int>()
        newMapSimpleObjectsAfter["one"] = 2
        newMapSimpleObjectsAfter["nine"] = 10
        newMapSimpleObjectsAfter["four"] = 4
        newMapSimpleObjectsAfter["five"] = 6
        val newMapComplexObjectsAfter = LinkedHashMap<String, MockVersionObject>()
        newMapComplexObjectsAfter["one"] = newBeforeSimpleFields().withStringOne("testThree")
        newMapComplexObjectsAfter["nine"] = newBeforeSimpleFields().withStringOne("testTen")
        newMapComplexObjectsAfter["four"] = newBeforeSimpleFields().withStringOne("testFive")
        newMapComplexObjectsAfter["five"] = newBeforeSimpleFields()

        val newAfter = newAfterSimpleFields()
                .withListObjects(newListObjectsAfter)
                .withSetObjects(newSetObjectsAfter)
                .withMapSimpleObjects(newMapSimpleObjectsAfter)
                .withMapComplexObjects(newMapComplexObjectsAfter)

        val statefulObject = versionManager.applyState(version, newBefore)

        assertEquals(statefulObject, newAfter)
    }

    @Test
    fun simpleFields_mapCorrectly() {
        val before = newBeforeSimpleFields()
        val after = newAfterSimpleFields()

        val version = versionManager.extractVersion(DiffPair(current = before, updated = after))

        val newBefore = newBeforeSimpleFields()
        val newAfter = newAfterSimpleFields()

        val statefulObject = versionManager.applyState(version, newBefore)

        assertEquals(statefulObject, newAfter)
    }

    private fun newBeforeSimpleFields(): MockVersionObject {
        val mockVersionObject = MockVersionObject()
        mockVersionObject.stringOne = "textOne"
        mockVersionObject.shortOne = 1.toShort()
        mockVersionObject.shortTwo = 2.toShort()
        mockVersionObject.integerOne = 1
        mockVersionObject.integerTwo = 2
        mockVersionObject.longOne = 2L
        mockVersionObject.longTwo = 3L
        mockVersionObject.doubleOne = 1.0
        mockVersionObject.doubleTwo = 2.0
        mockVersionObject.floatOne = 1.0f
        mockVersionObject.floatTwo = 2.0f
        mockVersionObject.bigIntegerOne = BigInteger.ONE
        mockVersionObject.bigDecimalOne = BigDecimal.ONE
        mockVersionObject.booleanOne = java.lang.Boolean.TRUE
        mockVersionObject.booleanTwo = java.lang.Boolean.FALSE
        mockVersionObject.mockEnumObject = MockEnumObject.A

        return mockVersionObject
    }

    private fun newAfterSimpleFields(): MockVersionObject {
        val mockVersionObject = MockVersionObject()
        mockVersionObject.stringOne = "textTwo"
        mockVersionObject.shortOne = 2.toShort()
        mockVersionObject.shortTwo = 1.toShort()
        mockVersionObject.integerOne = 2
        mockVersionObject.integerTwo = 1
        mockVersionObject.longOne = 1L
        mockVersionObject.longTwo = 2L
        mockVersionObject.doubleOne = 2.0
        mockVersionObject.doubleTwo = 1.0
        mockVersionObject.floatOne = 2.0f
        mockVersionObject.floatTwo = 1.0f
        mockVersionObject.bigIntegerOne = BigInteger.TEN
        mockVersionObject.bigDecimalOne = BigDecimal.TEN
        mockVersionObject.booleanOne = java.lang.Boolean.FALSE
        mockVersionObject.booleanTwo = java.lang.Boolean.TRUE
        mockVersionObject.mockEnumObject = MockEnumObject.B

        return mockVersionObject
    }
}