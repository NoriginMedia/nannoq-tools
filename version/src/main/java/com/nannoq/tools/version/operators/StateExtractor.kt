package com.nannoq.tools.version.operators

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.nannoq.tools.version.VersionUtils
import com.nannoq.tools.version.VersionUtils.Companion.ADD_TOKEN
import com.nannoq.tools.version.VersionUtils.Companion.COLLECTION_END_TOKEN
import com.nannoq.tools.version.VersionUtils.Companion.COLLECTION_START_TOKEN
import com.nannoq.tools.version.VersionUtils.Companion.DELETE_TOKEN
import com.nannoq.tools.version.VersionUtils.Companion.FIELD_SEPARATOR_TOKEN
import com.nannoq.tools.version.models.DiffPair
import com.nannoq.tools.version.models.IteratorId
import com.nannoq.tools.version.models.ObjectModification
import com.nannoq.tools.version.models.Version
import io.vertx.core.logging.LoggerFactory
import java.io.IOException
import java.lang.reflect.Field
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.stream.Collectors.toMap
import java.util.stream.IntStream

internal class StateExtractor(private val objectMapper: ObjectMapper, private val versionUtils: VersionUtils) {
    private val logger = LoggerFactory.getLogger(StateApplier::class.java)
    private val allClassFields = HashMap<Class<*>, Array<Field>>()

    @Throws(IllegalStateException::class)
    fun <T> extractVersion(pair: DiffPair<T>): Version {
        val failedExtract = AtomicBoolean()
        val changeMap = LinkedHashMap<String, ObjectModification>()

        try {
            doExtract(failedExtract, pair, "", changeMap)
        } catch (e: Exception) {
            failedStateExtact(failedExtract, "Error in extraction", e)
        }

        if (failedExtract.get()) throw IllegalStateException("Could not extract!")

        return Version(createdAt = Instant.now(), objectModificationMap = changeMap, correlationId = "", id = 1L)
    }

    private fun failedStateExtact(failedExtract: AtomicBoolean, message: String, e: Exception) {
        failedExtract.set(true)

        logger.error(message, e)
    }

    private fun doExtract(failedExtract: AtomicBoolean,
                          pair: DiffPair<*>, prepend: String,
                          changeMap: MutableMap<String, ObjectModification>) {
        if (pair.current == null && pair.updated == null) {
            failedStateExtact(failedExtract, "Both fields cannot be null!", IllegalArgumentException())

            throw IllegalArgumentException("Both fields cannot be null!")
        }

        val aClass = if (pair.current == null) pair.updated!!::class.java else pair.current.javaClass
        var declaredFields: Array<Field>? = allClassFields[aClass]

        if (declaredFields == null) {
            declaredFields = aClass.declaredFields
            allClassFields[aClass] = declaredFields
        }

        Arrays.stream(declaredFields!!)
                .filter { f -> f.getDeclaredAnnotation(IteratorId::class.java) == null }
                .forEach(processFields(failedExtract, pair, prepend, changeMap))
    }

    private fun processFields(failedExtract: AtomicBoolean,
                              pair: DiffPair<*>, prepend: String,
                              changeMap: MutableMap<String, ObjectModification>): (Field) -> Unit {
        return { field ->
            try {
                field.isAccessible = true

                when {
                    prepend.endsWith(COLLECTION_END_TOKEN) ->
                        setCollectionModificationFieldChange(pair, prepend, changeMap)
                    else -> processSimpleAndComplexFields(failedExtract, pair, prepend, changeMap, field)
                }
            } catch (e: IllegalAccessException) {
                failedStateExtact(failedExtract, "Could not handle extraction value", e)
            } catch (e: JsonProcessingException) {
                failedStateExtact(failedExtract, "Could not handle extraction value", e)
            }
        }
    }

    @Throws(JsonProcessingException::class)
    private fun setCollectionModificationFieldChange(pair: DiffPair<*>, prepend: String,
                                                     changeMap: MutableMap<String, ObjectModification>) {
        val current = pair.current
        val updated = pair.updated

        when {
            current == null -> changeMap[prepend] = objectModification(null,
                    if (updated != null) objectMapper.writeValueAsString(updated) else null)
            updated == null -> changeMap[prepend] = objectModification(current, null)
        }
    }

    @Throws(IllegalAccessException::class, JsonProcessingException::class)
    private fun processSimpleAndComplexFields(failedExtract: AtomicBoolean,
                                              pair: DiffPair<*>, prepend: String,
                                              changeMap: MutableMap<String, ObjectModification>, field: Field) {
        val current = if (pair.current == null) null else field.get(pair.current)
        val updated = if (pair.updated == null) null else field.get(pair.updated)

        when (field.type.simpleName) {
            "String",
            "short", "Short",
            "int", "Integer",
            "long", "Long",
            "double", "Double",
            "float", "Float",
            "boolean", "Boolean" -> if (current != updated) {
                changeMap[prepend + field.name] = objectModification(current, updated)
            }
            "BigInteger", "BigDecimal" -> if (current != updated) {
                changeMap[prepend + field.name] = objectModification(current?.toString(),
                        updated?.toString())
            }
            else -> {
                when {
                    field.type.isEnum -> when {
                        current != updated ->
                            changeMap[prepend + field.name] = objectModification(current, updated)
                    }
                    else -> extractComplexType(failedExtract, prepend, changeMap, field, current, updated)
                }
            }
        }
    }

    @Throws(JsonProcessingException::class)
    private fun extractComplexType(failedExtract: AtomicBoolean,
                                   prepend: String, changeMap: MutableMap<String, ObjectModification>,
                                   field: Field, current: Any?, updated: Any?) {
        when {
            current != null -> when (updated) {
                null -> changeMap[prepend + field.name] = objectModification(current, null)
                else -> {
                    nullCurrentValue(field, failedExtract, prepend, changeMap, current, updated)
                }
            }
            else -> if (updated != null) {
                changeMap[prepend + field.name] = objectModification(null, objectMapper.writeValueAsString(updated))
            }
        }
    }

    private fun nullCurrentValue(field: Field, failedExtract: AtomicBoolean, prepend: String,
                                 changeMap: MutableMap<String, ObjectModification>, current: Any?, updated: Any?) {
        val type = field.type

        when {
            type.isAssignableFrom(List::class.javaObjectType) ->
                extractFromList(failedExtract, prepend, changeMap, field, current as List<*>, updated as List<*>)
            type.isAssignableFrom(Map::class.javaObjectType) ->
                extractFromMap(failedExtract, prepend, changeMap, field,
                        current as MutableMap<*, *>, updated as Map<*, *>)
            type.isAssignableFrom(Set::class.javaObjectType) ->
                extractFromSet(failedExtract, prepend, changeMap, field, current as Set<*>, updated as Set<*>)
            else -> doExtract(failedExtract, versionPair(current, updated),
                    prepend + field.name + FIELD_SEPARATOR_TOKEN, changeMap)
        }
    }

    private fun extractFromList(failedExtract: AtomicBoolean,
                                prepend: String, changeMap: MutableMap<String, ObjectModification>,
                                field: Field, old: List<*>, updated: List<*>) {
        when {
            listModificationsPresent(old, updated) -> {
                old.forEach {
                    updatedExistingRecordsInCollection(failedExtract, prepend, changeMap, field, updated)(it!!)
                }

                IntStream.range(0, updated.size)
                        .forEach { addNewRecordsToChangeMapForList(
                                failedExtract, prepend, changeMap, field, old, updated)(it) }

                old.stream()
                        .filter { iteratorIdIsNotNull()(it!!) }
                        .forEach { addOldRecordRemovalToChangeMapForCollection(
                                failedExtract, prepend, changeMap, field, updated)(it!!) }
            }
            else -> IntStream.range(0, old.size).forEach(extractChangesInRecordsForUnmodifiedList(
                    failedExtract, prepend, changeMap, field, old, updated))
        }
    }

    private fun extractFromMap(failedExtract: AtomicBoolean,
                               prepend: String, changeMap: MutableMap<String, ObjectModification>,
                               field: Field, old: MutableMap<*, *>, updated: Map<*, *>) {
        try {
            val oldMap = makeMap(failedExtract, old)
            val newMap = makeMap(failedExtract, updated)

            addNewRecordsToChangeMapForMap(failedExtract, prepend, changeMap, field, oldMap, newMap)
            addRemovalOfOldRecordsToChangeMapForMap(failedExtract, prepend, changeMap, field, oldMap, newMap)

            oldMap.keys.forEach {
                extractChangesInRecordsForUnmodifiedMap(failedExtract, prepend, changeMap, field, oldMap, newMap)(it!!)
            }
        } catch (e: Exception) {
            failedStateExtact(failedExtract, "Error in extract from maps", e)
        }

    }

    @Throws(IOException::class)
    private fun makeMap(failedExtract: AtomicBoolean, map: Map<*, *>): MutableMap<*, *> {
        val complexType = map.isNotEmpty() && versionUtils.isComplexObject(map.values.iterator().next()!!::class.java)
        val jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(map), MutableMap::class.java)

        when {
            !complexType -> return jsonMap
            else -> {
                val type = map.values.iterator().next()!!::class.java

                return jsonMap.keys.stream()
                        .map<AbstractMap.SimpleEntry<String, Any>> { k ->
                            try {
                                val objectAsString = objectMapper.writeValueAsString(jsonMap[k])
                                val o = objectMapper.readValue(objectAsString, type)

                                AbstractMap.SimpleEntry<String, Any>(k as String?, o)
                            } catch (e: IOException) {
                                failedStateExtact(failedExtract, "Error in map extract creation", e)

                                null
                            }
                        }
                        .filter { Objects.nonNull(it) }
                        .collect(toMap({ it.key }, { it.value }, { i, _ -> i }, { LinkedHashMap<Any, Any>() }))
            }
        }
    }

    private fun extractFromSet(failedExtract: AtomicBoolean,
                               prepend: String, changeMap: MutableMap<String, ObjectModification>,
                               field: Field, old: Set<*>, updated: Set<*>) {
        when {
            listModificationsPresent(old, updated) -> {
                old.forEach {
                    updatedExistingRecordsInCollection(failedExtract, prepend, changeMap, field, updated)(it!!)
                }

                addNewRecordsToChangeMapForSet(failedExtract, prepend, changeMap, field, old, updated)

                old.stream()
                        .filter { iteratorIdIsNotNull()(it!!) }
                        .forEach { addOldRecordRemovalToChangeMapForCollection(
                                failedExtract, prepend, changeMap, field, updated)(it!!) }
            }
            else -> old.forEach {
                updatedExistingRecordsInCollection(failedExtract, prepend, changeMap, field, updated)(it!!)
            }
        }
    }

    private fun addOldRecordRemovalToChangeMapForCollection(failedExtract: AtomicBoolean,
                                                            prepend: String,
                                                            changeMap: MutableMap<String, ObjectModification>,
                                                            field: Field, collection: Collection<*>): (Any) -> Unit {
        return { record ->
            try {
                val iteratorIdField = versionUtils.getIteratorIdField(record)
                iteratorIdField?.isAccessible = true
                val id = iteratorIdField?.get(record)

                when {
                    collection.stream().noneMatch {
                        isNoneExistantInUpdatedList(failedExtract, iteratorIdField!!, id!!)(it!!)
                    } -> {
                        val versionPair = versionPair(record, null)

                        doExtract(failedExtract, versionPair,
                                prepend + DELETE_TOKEN + field.name +
                                        COLLECTION_START_TOKEN + id + COLLECTION_END_TOKEN,
                                changeMap)
                    }
                }
            } catch (e: IllegalAccessException) {
                logger.error("Could not remove element from list when comparing iteratorids")
            }
        }
    }

    private fun updatedExistingRecordsInCollection(failedExtract: AtomicBoolean,
                                                   prepend: String, changeMap: MutableMap<String, ObjectModification>,
                                                   field: Field, collection: Collection<*>): (Any) -> Unit {
        return { oldRecord ->
            try {
                val iteratorIdField = versionUtils.getIteratorIdField(oldRecord)
                iteratorIdField?.isAccessible = true
                val iteratorId = iteratorIdField?.get(oldRecord)

                val first = collection.stream()
                        .filter { updateExistsInOldList(iteratorId!!)(it!!) }
                        .findFirst()

                when {
                    first.isPresent && oldRecord != first.get() ->
                        doExtract(failedExtract, versionPair(oldRecord, first.get()),
                            prepend + field.name +
                                    COLLECTION_START_TOKEN + iteratorId + COLLECTION_END_TOKEN +
                                    FIELD_SEPARATOR_TOKEN,
                            changeMap)
                }
            } catch (e: NullPointerException) {
                failedStateExtact(failedExtract, "Could not check change in list at compare of iteratorids", e)
            } catch (e: IllegalAccessException) {
                failedStateExtact(failedExtract, "Could not check change in list at compare of iteratorids", e)
            }
        }
    }

    private fun setCollectionChangeInChangeMap(failedExtract: AtomicBoolean,
                                               prepend: String, changeMap: MutableMap<String, ObjectModification>,
                                               field: Field, i: Int, diffPair: DiffPair<*>,
                                               size: Int, size2: Int) {
        val indexValue = if (i >= size) size + size2 + i else i

        doExtract(failedExtract, diffPair,
                prepend + ADD_TOKEN + field.name +
                        COLLECTION_START_TOKEN + indexValue +
                        COLLECTION_END_TOKEN,
                changeMap)
    }

    private fun extractChangesInRecordsForUnmodifiedList(failedExtract: AtomicBoolean,
                                                         prepend: String,
                                                         changeMap: MutableMap<String, ObjectModification>,
                                                         field: Field,
                                                         old: List<*>, updated: List<*>): (Int) -> Unit {
        return { i ->
            when {
                old[i] != updated[i] -> {
                    val versionPair = versionPair(old[i], updated[i])

                    doExtract(failedExtract, versionPair,
                            prepend + field.name +
                                    COLLECTION_START_TOKEN + i + COLLECTION_END_TOKEN +
                                    FIELD_SEPARATOR_TOKEN,
                            changeMap)
                }
            }
        }
    }

    private fun addNewRecordsToChangeMapForList(failedExtract: AtomicBoolean,
                                                prepend: String,
                                                changeMap: MutableMap<String, ObjectModification>,
                                                field: Field, old: List<*>, updated: List<*>): (Int) -> Unit {
        return { i ->
            val o = updated[i]

            when {
                getIteratorId(o!!) == null -> {
                    val versionPair = versionPair(null, updated[i])

                    setCollectionChangeInChangeMap(failedExtract, prepend, changeMap, field, i, versionPair,
                            old.size, updated.size)
                }
            }
        }
    }

    private fun addNewRecordsToChangeMapForSet(failedExtract: AtomicBoolean,
                                               prepend: String, changeMap: MutableMap<String, ObjectModification>,
                                               field: Field, old: Set<*>, updated: Set<*>) {
        val itr = updated.iterator()

        var i = 0
        while (itr.hasNext()) {
            val o = itr.next()

            when {
                getIteratorId(o!!) == null -> {
                    val versionPair = versionPair(null, o)

                    setCollectionChangeInChangeMap(failedExtract, prepend, changeMap, field, i,
                            versionPair, old.size, updated.size)
                }
            }

            i++
        }
    }

    private fun addRemovalOfOldRecordsToChangeMapForMap(failedExtract: AtomicBoolean,
                                                        prepend: String,
                                                        changeMap: MutableMap<String, ObjectModification>,
                                                        field: Field, old: MutableMap<*, *>, updated: Map<*, *>) {
        val keysToRemove = ArrayList<Any>()

        old.keys.stream()
                .filter { record -> !updated.containsKey(record) }
                .forEach { record ->
                    val versionPair = versionPair(old[record], null)

                    doExtract(failedExtract, versionPair,
                            prepend + DELETE_TOKEN + field.name +
                                    COLLECTION_START_TOKEN + record + COLLECTION_END_TOKEN,
                            changeMap)

                    keysToRemove.add(record!!)
                }

        keysToRemove.forEach(Consumer { old.remove(it) })
    }

    private fun addNewRecordsToChangeMapForMap(failedExtract: AtomicBoolean,
                                               prepend: String, changeMap: MutableMap<String, ObjectModification>,
                                               field: Field, old: Map<*, *>, updated: Map<*, *>) {
        updated.keys.stream()
                .filter { record -> !old.containsKey(record) }
                .forEach { record ->
                    val versionPair = versionPair(null, updated[record])
                    val updatedObject = versionPair.updated
                    val changeMapFieldName = prepend + ADD_TOKEN + field.name +
                            COLLECTION_START_TOKEN + record + COLLECTION_END_TOKEN

                    when {
                        versionUtils.isComplexObject(updatedObject!!::class.java) -> try {
                            changeMap[changeMapFieldName] =
                                    objectModification(null, objectMapper.writeValueAsString(updatedObject))
                        } catch (e: JsonProcessingException) {
                            failedStateExtact(failedExtract, "Error in writing new object to map", e)
                            logger.error("Error in writing new object to map")
                        }
                        else -> changeMap[changeMapFieldName] = objectModification(null, updatedObject)
                    }
                }
    }

    private fun extractChangesInRecordsForUnmodifiedMap(failedExtract: AtomicBoolean,
                                                        prepend: String,
                                                        changeMap: MutableMap<String, ObjectModification>,
                                                        field: Field,
                                                        old: Map<*, *>, updated: Map<*, *>): (Any) -> Unit {
        return { k ->
            val oldObject = old[k]
            val updatedObject = updated[k]

            when {
                oldObject != updatedObject -> {
                    val versionPair = versionPair(oldObject, updatedObject)
                    val changeMapFieldName = prepend + field.name +
                            COLLECTION_START_TOKEN + k + COLLECTION_END_TOKEN

                    when {
                        oldObject != null && versionUtils.isComplexObject(oldObject.javaClass) ->
                            doExtract(failedExtract, versionPair, changeMapFieldName + FIELD_SEPARATOR_TOKEN, changeMap)
                        else -> changeMap[changeMapFieldName] = objectModification(null, updatedObject)
                    }
                }
            }
        }
    }

    private fun listModificationsPresent(old: Collection<*>, updated: Collection<*>): Boolean {
        return old.size != updated.size ||
                nullIteratorIdsInUpdatedList(updated) ||
                oldIteratorIdsNotPresentInUpdatedList(old, updated)
    }

    private fun updateExistsInOldList(iteratorId: Any): (Any) -> Boolean {
        return { updatedRecord ->
            try {
                val updatedIteratorIdField = versionUtils.getIteratorIdField(updatedRecord)
                updatedIteratorIdField?.isAccessible = true
                val updatedIteratorId = updatedIteratorIdField?.get(updatedRecord)

                iteratorId == updatedIteratorId
            } catch (e: NullPointerException) {
                false
            } catch (e: IllegalAccessException) {
                false
            }
        }
    }

    private fun nullIteratorIdsInUpdatedList(updated: Collection<*>): Boolean {

        return updated.stream().anyMatch { iteratorIdIsNull()(it!!) }
    }

    private fun oldIteratorIdsNotPresentInUpdatedList(old: Collection<*>, updated: Collection<*>): Boolean {
        return old.stream()
                .map { it?.let { it1 -> this.getIteratorId(it1) } }
                .noneMatch { it?.let { it1 -> existsInUpdateList(updated)(it1) }!! }
    }

    private fun existsInUpdateList(updated: Collection<*>): (Any) -> Boolean {
        return { id ->
            updated.stream()
                    .map { it?.let { it1 -> this.getIteratorId(it1) } }
                    .anyMatch { updatedId -> updatedId != null && updatedId == id }
        }
    }

    private fun isNoneExistantInUpdatedList(failedExtract: AtomicBoolean,
                                            iteratorIdField: Field, iteratorId: Any): (Any) -> Boolean {
        return { updateRecord ->
            try {
                val iteratorIdOnOldRecord = iteratorIdField.get(updateRecord)

                iteratorIdOnOldRecord === iteratorId
            } catch (e: NullPointerException) {
                failedStateExtact(failedExtract, "Could not compare iteratorids", e)

                false
            } catch (e: IllegalAccessException) {
                failedStateExtact(failedExtract, "Could not compare iteratorids", e)

                false
            }
        }
    }

    private fun iteratorIdIsNull(): (Any) -> Boolean {
        return { record -> getIteratorId(record) == null }
    }

    private fun iteratorIdIsNotNull(): (Any) -> Boolean {
        return { record -> getIteratorId(record) != null }
    }

    private fun getIteratorId(record: Any): Any? {
        return try {
            val iteratorIdField = versionUtils.getIteratorIdField(record)
            iteratorIdField?.isAccessible = true

            iteratorIdField?.get(record)
        } catch (e: NullPointerException) {
            null
        } catch (e: IllegalAccessException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun versionPair(left: Any?, right: Any?): DiffPair<*> {
        return DiffPair(current = left, updated = right)
    }

    private fun objectModification(old: Any?, updated: Any?): ObjectModification {
        return ObjectModification(oldValue = old, newValue = updated)
    }
}
