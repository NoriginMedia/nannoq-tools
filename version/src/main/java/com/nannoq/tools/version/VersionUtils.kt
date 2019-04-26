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

package com.nannoq.tools.version

import com.nannoq.tools.version.models.IteratorId
import java.lang.reflect.Field
import java.util.Arrays
import java.util.function.Predicate

class VersionUtils {
    private val isFieldVersionIteratorId: Predicate<Field>
        get() = Predicate { f -> f.getDeclaredAnnotation(IteratorId::class.java) != null }

    @Throws(NoSuchFieldException::class)
    internal fun getField(klazz: Class<*>, field: String): Field? {
        if (FIELD_MAP.containsKey(klazz)) {
            val stringFieldMap = FIELD_MAP[klazz] ?: throw NoSuchFieldException()
            var newField: Field? = stringFieldMap[field]

            if (newField != null) {
                return newField
            }

            newField = klazz.getDeclaredField(field)
            stringFieldMap[field] = newField
            FIELD_MAP[klazz] = stringFieldMap

            return newField
        } else {
            val declaredField = klazz.getDeclaredField(field)

            FIELD_MAP[klazz] = object : HashMap<String, Field>() {
                init {
                    put(field, declaredField)
                }
            }

            return declaredField
        }
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    internal fun getFieldObject(klazz: Class<*>, field: String, obj: Any): Any {
        if (!field.contains(COLLECTION_START_TOKEN)) {
            val declaredField = getField(klazz, field)
            declaredField!!.isAccessible = true

            return declaredField.get(obj)
        }

        val split = field.split(("[\\$COLLECTION_START_TOKEN]").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val fieldName = split[0]
        val declaredField = getField(klazz, fieldName)
        declaredField!!.isAccessible = true
        val type = declaredField.type

        when {
            type.isAssignableFrom(List::class.javaObjectType) -> {
                val fieldIndex = Integer.parseInt(split[1].substring(0, split[1].length - 1))

                @Suppress("UNCHECKED_CAST")
                return (declaredField.get(obj) as List<Any>)[fieldIndex]
            }
            type.isAssignableFrom(Map::class.javaObjectType) -> {
                return (declaredField.get(obj) as Map<*, *>)[split[1].substring(0, split[1].length - 1)]!!
            }
            type.isAssignableFrom(Set::class.javaObjectType) -> {
                val fieldIndex = Integer.parseInt(split[1].substring(0, split[1].length - 1))
                val itr = (declaredField.get(obj) as Set<*>).iterator()

                var i = 0
                while (itr.hasNext()) {
                    val o = itr.next()

                    if (i == fieldIndex) {
                        return o!!
                    }

                    i++
                }

                throw IllegalArgumentException("Kunne ikke finne element i collection!")
            }
            else -> throw IllegalArgumentException("Dette er ikke en collection!")
        }
    }

    internal fun getIteratorIdField(iteratableObj: Any): Field? {
        return try {
            getField(iteratableObj.javaClass, DEFAULT_ITERATOR_ID)
        } catch (nsfe: NoSuchFieldException) {
            val first = Arrays.stream(iteratableObj.javaClass.declaredFields)
                    .filter(isFieldVersionIteratorId)
                    .findFirst()

            first.get()
        }
    }

    @Throws(IllegalAccessException::class)
    internal fun <T> getListFromObj(obj: T, field: Field): MutableList<*>? {
        return getListFromObj(obj, field, true)
    }

    @Throws(IllegalAccessException::class)
    internal fun <T> getListFromObj(obj: T, field: Field, makeNew: Boolean): MutableList<*>? {
        var list: MutableList<*>? = field.get(obj) as MutableList<*>?

        if (list == null && makeNew) {
            list = ArrayList<Any>()
            field.set(obj, list)
        }

        return list
    }

    @Throws(IllegalAccessException::class)
    internal fun <T> getMapFromObj(obj: T, field: Field): MutableMap<*, *>? {
        return getMapFromObj(obj, field, true)
    }

    @Throws(IllegalAccessException::class)
    internal fun <T> getMapFromObj(obj: T, field: Field, makeNew: Boolean): MutableMap<*, *>? {
        var map: MutableMap<*, *>? = field.get(obj) as MutableMap<*, *>?

        if (map == null && makeNew) {
            map = LinkedHashMap<Any, Any>()
            field.set(obj, map)
        }

        return map
    }

    @Throws(IllegalAccessException::class)
    internal fun <T> getSetFromObj(obj: T, field: Field): MutableSet<*>? {
        return getSetFromObj(obj, field, true)
    }

    @Throws(IllegalAccessException::class)
    internal fun <T> getSetFromObj(obj: T, field: Field, makeNew: Boolean): MutableSet<*>? {
        var set: MutableSet<*>? = field.get(obj) as MutableSet<*>?

        if (set == null && makeNew) {
            set = LinkedHashSet<Any>()
            field.set(obj, set)
        }

        return set
    }

    @Throws(NoSuchFieldException::class)
    internal fun getAddOrRemovalField(klazz: Class<*>, addRemoveField: String): Field {
        val split = addRemoveField.split("[\\[]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val fieldName = if (split[0].contains("_")) split[0].split("[_]".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[2] else split[0]
        val declaredField = getField(klazz, fieldName)
        declaredField!!.isAccessible = true

        return declaredField
    }

    internal fun isComplexObject(aClass: Class<*>): Boolean {
        return when (aClass.simpleName) {
            "String",
            "short", "Short",
            "int", "Integer",
            "long", "Long",
            "double", "Double",
            "float", "Float",
            "boolean", "Boolean",
            "BigInteger", "BigDecimal" -> false
            else -> true
        }
    }

    companion object {
        internal const val ADD_TOKEN = "_ADD_"
        internal const val DELETE_TOKEN = "_DELETE_"
        internal const val FIELD_SEPARATOR_TOKEN = "."
        internal const val COLLECTION_START_TOKEN = "["
        internal const val COLLECTION_END_TOKEN = "]"
        private const val DEFAULT_ITERATOR_ID = "iteratorId"
        @Suppress("PrivatePropertyName")
        private val FIELD_MAP = HashMap<Class<*>, MutableMap<String, Field>>()
    }
}
