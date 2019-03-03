package com.nannoq.tools.version.operators

import com.fasterxml.jackson.databind.ObjectMapper
import com.nannoq.tools.version.VersionUtils
import com.nannoq.tools.version.VersionUtils.Companion.ADD_TOKEN
import com.nannoq.tools.version.VersionUtils.Companion.COLLECTION_END_TOKEN
import com.nannoq.tools.version.VersionUtils.Companion.COLLECTION_START_TOKEN
import com.nannoq.tools.version.VersionUtils.Companion.DELETE_TOKEN
import com.nannoq.tools.version.VersionUtils.Companion.FIELD_SEPARATOR_TOKEN
import com.nannoq.tools.version.models.ObjectModification
import com.nannoq.tools.version.models.Version
import io.vertx.core.AsyncResult
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.logging.LoggerFactory
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.AbstractMap.SimpleEntry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.stream.Collectors.*
import java.util.stream.IntStream
import java.util.stream.Stream

internal class StateApplier(private val objectMapper: ObjectMapper,
                            private val versionUtils: VersionUtils) {
    private val logger = LoggerFactory.getLogger(StateApplier::class.java)

    fun <T: Any> applyState(version: Version, obj: T, handler: Handler<AsyncResult<T>>): StateApplier {
        handler.handle(succeededFuture(applyState(version, obj)))

        return this
    }

    @Throws(IllegalStateException::class)
    fun <T: Any> applyState(version: Version, obj: T): T {
        val failedApply = AtomicBoolean()
        val changeMap = version.objectModificationMap

        try {
            val klazz = obj::class.java

            // stores all complex object refs when looking at simple fields, for processing in stage 2
            val complexFieldChangeMap = HashMap<String, MutableMap<String, ObjectModification>>()

            // separates normal fields and collection fields
            val collect = changeMap.keys.stream()
                    .collect(partitioningBy { k -> !k.contains(FIELD_SEPARATOR_TOKEN) && isAddRemovalField(k) })

            // stage 1, simple fields
            collect[false]?.forEach { field ->
                applySimpleFields(failedApply, changeMap.toMutableMap(), obj, klazz, complexFieldChangeMap, field)
            }

            // stage 2, complex objects
            complexFieldChangeMap.keys.parallelStream()
                    .forEach { applyComplexFields(failedApply, obj, klazz, complexFieldChangeMap)(it!!) }

            // stage 3, apply collection modifications
            applyCollectionFields(failedApply, collect[true]!!, obj, klazz, changeMap)
        } catch (e: Exception) {
            failedStateApplication(failedApply, "Error in state application", e)
        }

        if (failedApply.get()) {
            throw IllegalStateException("Application of state failed, could not continue!")
        }

        return obj
    }

    private fun failedStateApplication(failedApply: AtomicBoolean, message: String, e: Exception) {
        failedApply.set(true)

        logger.error(message, e)
    }

    private fun isAddRemovalField(k: String): Boolean {
        return k.startsWith(DELETE_TOKEN) || k.startsWith(ADD_TOKEN)
    }

    private fun <T> applySimpleFields(failedApply: AtomicBoolean,
                                      fieldChangeMap: MutableMap<String, ObjectModification>,
                                      obj: T,
                                      klazz: Class<*>,
                                      complexFieldChangeMap: MutableMap<String, MutableMap<String, ObjectModification>>,
                                      field: String) {
        val isSuper = field.contains(FIELD_SEPARATOR_TOKEN)
        val isSimpleMapValue = field.endsWith(COLLECTION_END_TOKEN)

        try {
            if (isSimpleMapValue && !isSuper) {
                setSimpleMapValues(klazz, obj, field, fieldChangeMap)
            } else if (isSuper) {
                val subObjectClassFieldSplit = field.split(("\\" + FIELD_SEPARATOR_TOKEN).toRegex(), 2).toTypedArray()
                val klazzField = subObjectClassFieldSplit[0]
                val subObjectField = subObjectClassFieldSplit[1]
                complexFieldChangeMap.putIfAbsent(klazzField, HashMap())
                complexFieldChangeMap[klazzField]?.put(subObjectField, fieldChangeMap[field]!!)
            } else {
                val objectModification = fieldChangeMap[field]
                val declaredField = versionUtils.getField(klazz, field)
                declaredField?.isAccessible = true

                setNewFieldValue(failedApply, klazz, obj!!, field, objectModification!!, declaredField!!)
            }
        } catch (e: NoSuchFieldException) {
            failedStateApplication(failedApply, "Could not find field ( $field ) for state application!", e)
        } catch (e: IllegalAccessException) {
            failedStateApplication(failedApply, "Could not find field ( $field ) for state application!", e)
        } catch (e: IllegalArgumentException) {
            failedStateApplication(failedApply, "Could not find field ( $field ) for state application!", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun <T> setSimpleMapValues(klazz: Class<*>, obj: T,
                                       field: String,
                                       fieldChangeMap: MutableMap<String, ObjectModification>) {
        val splitValue = field.split(("[\\$COLLECTION_START_TOKEN]").toRegex())
        val split = splitValue.dropLastWhile { it.isEmpty() }.toTypedArray()
        val declaredField = versionUtils.getField(klazz, split[0])
        declaredField?.isAccessible = true
        val map = versionUtils.getMapFromObj(obj, declaredField!!)

        if (map?.size!! > 0) {
            val type = map.values.iterator().next()!!::class.java.simpleName
            val asString = fieldChangeMap[field]?.newValue.toString()
            val secondSplitValue = split[1].split("[$COLLECTION_END_TOKEN]".toRegex())
            val key = secondSplitValue.dropLastWhile { it.isEmpty() }.toTypedArray()[0]

            (map as MutableMap<Any, Any?>)[key] = buildSimpleValue(asString, type)
        }
    }

    private fun <T> applyComplexFields(failedApply: AtomicBoolean,
                                       obj: T,
                                       klazz: Class<*>,
                                       complexChangeMap: Map<String, Map<String, ObjectModification>>)
            : (String) -> Unit {
        return { field ->
            try {
                val fieldObject = versionUtils.getFieldObject(klazz, field, obj!!)

                applyState(Version(objectModificationMap = complexChangeMap.getValue(field)), fieldObject)
            } catch (e: NoSuchFieldException) {
                failedStateApplication(failedApply, "Could not find complex field ( $field ) for change!", e)
            } catch (e: IllegalAccessException) {
                failedStateApplication(failedApply, "Could not find complex field ( $field ) for change!", e)
            }
        }
    }

    private fun <T> applyCollectionFields(failedApply: AtomicBoolean,
                                          additionAndRemovalFields: List<String>,
                                          obj: T,
                                          klazz: Class<*>,
                                          changeMap: Map<String, ObjectModification>) {
        if (additionAndRemovalFields.isNotEmpty()) {
            val deletionMap = additionAndRemovalFields.stream()
                    .collect(partitioningBy { field -> field.startsWith(ADD_TOKEN) })
            val setToListConvertMap = HashMap<String, MutableList<*>>()

            // remove records
            performCollectionUpdates(failedApply, klazz, obj!!, setToListConvertMap, deletionMap[false]!!, changeMap)

            // add records
            performCollectionUpdates(failedApply, klazz, obj, setToListConvertMap, deletionMap[true]!!, changeMap)

            // remove any nulls
            trimNullsOnAllModifiedCollections(failedApply, obj, klazz, deletionMap, setToListConvertMap)
        }
    }

    private fun <T> trimNullsOnAllModifiedCollections(failedApply: AtomicBoolean,
                                                      obj: T, klazz: Class<*>,
                                                      deletionMap: Map<Boolean, List<String>>,
                                                      setToListConversionMap: Map<String, List<*>>) {
        Stream.concat(deletionMap[false]?.stream(), deletionMap[true]?.stream())
                .forEach { addRemoveField ->
                    try {
                        val declaredField = versionUtils.getAddOrRemovalField(klazz, addRemoveField)
                        val type = declaredField.type

                        when {
                            type.isAssignableFrom(Set::class.java) ->
                                trimSetNulls(obj, setToListConversionMap, declaredField)
                            type.isAssignableFrom(List::class.java) -> trimListNulls(obj, declaredField)
                            type.isAssignableFrom(Map::class.java) -> trimMapNulls(obj, declaredField)
                        }
                    } catch (e: NoSuchFieldException) {
                        failedStateApplication(failedApply, "Could not get field for collection trim", e)
                    } catch (e: IllegalAccessException) {
                        failedStateApplication(failedApply, "Could not get field for collection trim", e)
                    }
                }
    }

    @Throws(IllegalAccessException::class)
    private fun <T> trimSetNulls(obj: T, setToListConversionMap: Map<String, List<*>>, declaredField: Field) {
        val name = declaredField.name

        if (setToListConversionMap.containsKey(name)) {
            val collect = setToListConversionMap[name]?.stream()
                    ?.filter { Objects.nonNull(it) }
                    ?.collect(toList()) as List<*>

            declaredField.set(obj, LinkedHashSet(collect))
        }
    }

    @Throws(IllegalAccessException::class)
    private fun <T> trimListNulls(obj: T, declaredField: Field) {
        val list = versionUtils.getListFromObj(obj, declaredField)

        if (list != null) {
            declaredField.set(obj, list.stream()
                    .filter { Objects.nonNull(it) }
                    .collect(toList()))
        }
    }

    @Throws(IllegalAccessException::class)
    private fun <T> trimMapNulls(obj: T, declaredField: Field) {
        val map = versionUtils.getMapFromObj(obj, declaredField)

        if (map != null) {
            val collect = HashSet<Map.Entry<*, *>>(map.entries).stream()
                    .filter { e -> e.value == null }
                    .map { it.key }
                    .collect(toList())

            collect.forEach(Consumer { map.remove(it) })
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun performCollectionUpdates(failedApply: AtomicBoolean,
                                         klazz: Class<*>, obj: Any,
                                         setToListConversionMap: MutableMap<String, MutableList<*>>,
                                         fields: List<String>,
                                         fieldChangeMap: Map<String, ObjectModification>) {
        fields.forEach { field ->
            try {
                val objectModification = fieldChangeMap[field]
                val splitValue = field.split("[\\$COLLECTION_START_TOKEN]".toRegex())
                val split = splitValue.dropLastWhile { it.isEmpty() }.toTypedArray()
                val declaredField = versionUtils.getAddOrRemovalField(klazz, field)
                val type = declaredField.type

                when {
                    type.isAssignableFrom(List::class.java) ->
                        performListUpdates(versionUtils.getListFromObj(obj, declaredField)!!,
                            field, objectModification!!, split[1], declaredField)
                    type.isAssignableFrom(Set::class.java) -> performSetUpdates(klazz, obj, field,
                            setToListConversionMap, objectModification!!, split[1], declaredField)
                    type.isAssignableFrom(Map::class.java) -> performMapUpdates(
                            (versionUtils.getMapFromObj(obj, declaredField) as MutableMap<Any, Any?>?)!!,
                            objectModification!!, split[1], declaredField)
                    else -> throw IllegalAccessException("This field is not a list or map!")
                }
            } catch (e: NoSuchFieldException) {
                failedStateApplication(failedApply, "Could not handle collection", e)
            } catch (e: IllegalAccessException) {
                failedStateApplication(failedApply, "Could not handle collection", e)
            } catch (e: ClassNotFoundException) {
                failedStateApplication(failedApply, "Could not handle collection", e)
            } catch (e: IOException) {
                failedStateApplication(failedApply, "Could not handle collection", e)
            }
        }
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun performMapUpdates(mapFromObj: MutableMap<Any, Any?>,
                                  objectModification: ObjectModification,
                                  split: String,
                                  declaredField: Field) {
        val key = split.substring(0, split.length - 1)
        val newValue = objectModification.newValue

        if (newValue == null) {
            mapFromObj.remove(key)
        } else if (objectModification.oldValue == null) {
            val genericType = declaredField.genericType as ParameterizedType
            val aClass = Class.forName(genericType.actualTypeArguments[1].typeName)
            val objectToSet = if (versionUtils.isComplexObject(aClass)) {
                objectMapper.readValue(newValue.toString(), aClass)
            } else {
                buildSimpleValue(newValue.toString(), newValue.javaClass.simpleName)
            }

            mapFromObj[key] = objectToSet
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalAccessException::class, ClassNotFoundException::class,
            IOException::class, NoSuchFieldException::class)
    private fun performSetUpdates(klazz: Class<*>, obj: Any, field: String,
                                  setToListConversionMap: MutableMap<String, MutableList<*>>,
                                  objectModification: ObjectModification,
                                  split: String, declaredField: Field) {
        val fieldIndex = Integer.parseInt(split.substring(0, split.length - 1))
        val addOrRemoveField = versionUtils.getAddOrRemovalField(klazz, field).name
        var list: MutableList<*>? = setToListConversionMap[addOrRemoveField]

        if (list == null) {
            list = ArrayList(versionUtils.getSetFromObj(obj, declaredField))
            setToListConversionMap[addOrRemoveField] = list
        }

        doCollectionUpdate(field, objectModification, declaredField, fieldIndex, list as MutableList<Any?>)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(ClassNotFoundException::class, IOException::class)
    private fun performListUpdates(listFromObj: MutableList<*>, field: String,
                                   objectModification: ObjectModification, split: String, declaredField: Field) {
        val fieldIndex = Integer.parseInt(split.substring(0, split.length - 1))

        doCollectionUpdate(field, objectModification, declaredField, fieldIndex, listFromObj as MutableList<Any?>)
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    private fun doCollectionUpdate(field: String, objectModification: ObjectModification, declaredField: Field,
                                   fieldIndex: Int, list: MutableList<Any?>) {
        when {
            field.startsWith(DELETE_TOKEN) -> list[fieldIndex] = null
            field.startsWith(ADD_TOKEN) -> {
                handleAddToken(objectModification, declaredField, fieldIndex, list)
            }
        }
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    private fun handleAddToken(objectModification: ObjectModification, declaredField: Field,
                               fieldIndex: Int, list: MutableList<Any?>) {
        val o = readObjectFromGenericType(objectModification, declaredField)

        when {
            fieldIndex >= list.size -> list.add(o)
            else -> {
                val nullOffset = IntStream.range(0, fieldIndex)
                        .map { i -> if (list[i] == null) 1 else 0 }
                        .sum()

                list.add(fieldIndex + nullOffset, o)
            }
        }
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    private fun readObjectFromGenericType(objectModification: ObjectModification, declaredField: Field,
                                          argumentNum: Int = 0): Any {
        val genericType = declaredField.genericType as ParameterizedType
        val aClass = Class.forName(genericType.actualTypeArguments[argumentNum].typeName)

        return objectMapper.readValue(objectModification.newValue.toString(), aClass)
    }

    @Throws(IllegalAccessException::class)
    private fun setNewFieldValue(failedApply: AtomicBoolean,
                                 klazz: Class<*>, obj: Any,
                                 field: String,
                                 objectModification: ObjectModification,
                                 declaredField: Field) {
        val newFieldString = if (objectModification.newValue != null) objectModification.newValue.toString() else null
        val typeName = declaredField.type.simpleName

        when (typeName) {
            "String",
            "short", "Short",
            "int", "Integer",
            "long", "Long",
            "double", "Double",
            "float", "Float",
            "boolean", "Boolean",
            "BigDecimal", "BigInteger" ->
                doSet(failedApply, objectModification, declaredField, obj, buildSimpleValue(newFieldString, typeName))
            "Date" ->
                doSet(failedApply, objectModification, declaredField, obj, objectModification.newValue)
            else -> processComplexOrCollectionFields(failedApply, klazz, obj, field, objectModification, declaredField)
        }
    }

    private fun buildSimpleValue(valueAsString: String?, typeOfValue: String): Any? {
        if (valueAsString == null) return null

        return when (typeOfValue) {
            "String" -> valueAsString
            "short", "Short" -> java.lang.Short.valueOf(valueAsString)
            "int", "Integer" -> Integer.valueOf(valueAsString)
            "long", "Long" -> java.lang.Long.valueOf(valueAsString)
            "double", "Double" -> java.lang.Double.valueOf(valueAsString)
            "float", "Float" -> java.lang.Float.valueOf(valueAsString)
            "boolean", "Boolean" -> java.lang.Boolean.valueOf(valueAsString)
            "BigDecimal" -> BigDecimal(valueAsString)
            "BigInteger" -> BigInteger(valueAsString)
            "Date" -> Date.parse(valueAsString)
            else -> null
        }
    }

    private fun processComplexOrCollectionFields(failedApply: AtomicBoolean,
                                                 klazz: Class<*>, obj: Any, field: String,
                                                 objectModification: ObjectModification, declaredField: Field) {
        try {
            val type = declaredField.type
            val oldValue = objectModification.oldValue
            val newValue = objectModification.newValue

            when {
                type.isEnum -> setEnumField(obj, declaredField, type, newValue!!)
                else -> setNonEnumField(failedApply, klazz, obj, field,
                        objectModification, declaredField, type, oldValue, newValue)
            }
        } catch (e: Exception) {
            failedStateApplication(failedApply, "Cannot handle this type!", e)

            throw IllegalArgumentException()
        }
    }

    @Throws(IllegalAccessException::class, InvocationTargetException::class, NoSuchMethodException::class)
    private fun setEnumField(obj: Any, declaredField: Field, type: Class<*>, newValue: Any) {
        val jsonString = newValue.toString()

        declaredField.set(obj, type.getDeclaredMethod("valueOf", String::class.java).invoke(null, jsonString))
    }

    @Throws(IOException::class, IllegalAccessException::class, NoSuchFieldException::class)
    private fun setNonEnumField(failedApply: AtomicBoolean,
                                klazz: Class<*>, obj: Any, field: String,
                                objectModification: ObjectModification, declaredField: Field,
                                type: Class<*>, oldValue: Any?, newValue: Any?) {
        when {
            oldValue == null && newValue != null ->
                newComplexOrCollection(failedApply, obj, objectModification, declaredField, type)
            oldValue != null && newValue == null -> declaredField.set(obj, null)
            else -> doSet(failedApply, objectModification, declaredField, obj,
                    versionUtils.getFieldObject(klazz, field, obj))
        }
    }

    @Throws(IOException::class, IllegalAccessException::class)
    private fun newComplexOrCollection(failedApply: AtomicBoolean,
                                       obj: Any, objectModification: ObjectModification, declaredField: Field,
                                       type: Class<*>) {
        val jsonString = objectModification.newValue.toString()

        when {
            type.isAssignableFrom(List::class.java) -> setListValue(failedApply, obj, declaredField, jsonString)
            type.isAssignableFrom(Set::class.java) -> setSetValue(failedApply, obj, declaredField, jsonString)
            type.isAssignableFrom(Map::class.java) -> setMapValue(failedApply, obj, declaredField, jsonString)
            else -> // non-collection complex field
                declaredField.set(obj, objectMapper.readValue(jsonString, type))
        }
    }

    @Throws(IOException::class, IllegalAccessException::class)
    private fun setListValue(failedApply: AtomicBoolean,
                             obj: Any, declaredField: Field, jsonString: String) {
        val list = objectMapper.readValue(jsonString, List::class.java)

        declaredField.set(obj, createNewListFromChangeMap(failedApply, list, declaredField))
    }

    @Throws(IOException::class, IllegalAccessException::class)
    private fun setSetValue(failedApply: AtomicBoolean,
                            obj: Any, declaredField: Field, jsonString: String) {
        val list = objectMapper.readValue(jsonString, List::class.java)


        declaredField.set(obj, LinkedHashSet(createNewListFromChangeMap(failedApply, list, declaredField)))
    }

    @Throws(IOException::class, IllegalAccessException::class)
    private fun setMapValue(failedApply: AtomicBoolean,
                            obj: Any, declaredField: Field, jsonString: String) {
        val map = objectMapper.readValue(jsonString, Map::class.java)

        declaredField.set(obj, map.keys.stream()
                .map {
                    @Suppress("UNCHECKED_CAST")
                    buildEntryWithParsedJsonObject(failedApply, declaredField, map as Map<String, Any>)(it as String)
                }
                .filter { Objects.nonNull(it) }
                .collect(toMap({ it?.key }, { it?.value }, { i, _ -> i }, { LinkedHashMap<String, Any>() })))
    }

    private fun buildEntryWithParsedJsonObject(failedApply: AtomicBoolean,
                                               declaredField: Field,
                                               map: Map<String, Any>): (String) -> SimpleEntry<String, Any>? {
        return { k ->
            try {
                SimpleEntry(k, readObjectFromGenericType(
                        ObjectModification(newValue = objectMapper.writeValueAsString(map[k])), declaredField, 1))
            } catch (e: ClassNotFoundException) {
                failedStateApplication(failedApply, "Could not handle this type!", e)

                null
            } catch (e: IOException) {
                failedStateApplication(failedApply, "Could not handle this type!", e)

                null
            }
        }
    }

    private fun createNewListFromChangeMap(failedApply: AtomicBoolean,
                                           list: List<*>, declaredField: Field): List<*> {
        return list.stream()
                .map { o ->
                    try {
                        readObjectFromGenericType(
                                ObjectModification(newValue =objectMapper.writeValueAsString(o)), declaredField)
                    } catch (e: ClassNotFoundException) {
                        failedStateApplication(failedApply, "Could not handle this type!", e)

                        null
                    } catch (e: IOException) {
                        failedStateApplication(failedApply, "Could not handle this type!", e)

                        null
                    }
                }
                .filter { Objects.nonNull(it) }
                .collect(toList())
    }

    @Throws(IllegalAccessException::class)
    private fun doSet(failedApply: AtomicBoolean,
                      objectModification: ObjectModification, declaredField: Field, obj: Any, value: Any?) {
        val currentValueAsString = declaredField.get(obj)?.toString()
        val oldValueAsString = objectModification.oldValue?.toString()

        when (oldValueAsString) {
            currentValueAsString -> declaredField.set(obj, value)
            else -> {
                failedApply.set(true)

                throw IllegalArgumentException("Old value does not fit current state!")
            }
        }
    }
}