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

package com.nannoq.tools.version.operators

import com.nannoq.tools.version.VersionUtils
import io.vertx.core.AsyncResult
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.logging.LoggerFactory
import java.lang.reflect.Field
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors.toList
import java.util.stream.IntStream

internal class IteratorIdManager(private val versionUtils: VersionUtils) {
    private val logger = LoggerFactory.getLogger(StateApplier::class.java)

    fun <T> setIteratorIds(obj: T, handler: Handler<AsyncResult<T>>): IteratorIdManager {
        handler.handle(succeededFuture(setIteratorIds(listOf(obj)).iterator().next()))

        return this
    }

    @Throws(IllegalStateException::class)
    private fun <T> setIteratorIds(obj: T): T {
        return setIteratorIds(listOf(obj)).iterator().next()
    }

    fun <T> setIteratorIds(objs: Collection<T>, handler: Handler<AsyncResult<Collection<T>>>): IteratorIdManager {
        handler.handle(succeededFuture(setIteratorIds(objs)))

        return this
    }

    @Throws(IllegalStateException::class)
    fun <T> setIteratorIds(objs: Collection<T>): Collection<T> {
        val failedIteratorApplication = AtomicBoolean()
        var collect: Collection<T>? = null

        try {
            collect = objs.parallelStream()
                    .peek { o -> applyIteratorIdToApplicableFields()(failedIteratorApplication, o!!) }
                    .collect(toList())
        } catch (e: Exception) {
            failedIteratorApply(failedIteratorApplication, "Error in iteratorId application", e)
        }

        if (failedIteratorApplication.get()) {
            throw IllegalStateException("Error in iteratorapplication, could not continue!")
        }

        return collect.let { objs }
    }

    private fun failedIteratorApply(failedIteratorApplication: AtomicBoolean, message: String, e: Exception) {
        failedIteratorApplication.set(true)

        logger.error(message, e)
    }

    private fun applyIteratorIdToApplicableFields(): (AtomicBoolean, T: Any) -> Unit {
        return { failedIteratorApplication, obj ->
            Arrays.stream<Field>(obj::class.java.declaredFields).forEach { field ->
                field.isAccessible = true

                when (field.type.simpleName) {
                    "String",
                    "short", "Short",
                    "int", "Integer",
                    "long", "Long",
                    "double", "Double",
                    "float", "Float",
                    "boolean", "Boolean",
                    "BigInteger", "BigDecimal" -> {
                    }
                    else -> applyIteratorIds(failedIteratorApplication, obj, field)
                }
            }
        }
    }

    private fun <T> applyIteratorIds(failedIteratorApplication: AtomicBoolean, obj: T, field: Field) {
        if (field.type.isEnum) return

        try {
            when {
                field.type.isAssignableFrom(List::class.javaObjectType) ->
                    setIteratorIdsOnListField(failedIteratorApplication, obj, field)
                field.type.isAssignableFrom(Set::class.javaObjectType) ->
                    setIteratorIdsOnSetField(failedIteratorApplication, obj, field)
                else -> {
                    field.isAccessible = true
                    val o = field.get(obj)

                    if (o != null) setIteratorIds(o)
                }
            }
        } catch (e: IllegalAccessException) {
            failedIteratorApply(failedIteratorApplication, "Could not handle collection", e)
        }
    }

    @Throws(IllegalAccessException::class)
    private fun <T> setIteratorIdsOnListField(failedIteratorApplication: AtomicBoolean, obj: T, field: Field) {
        val list = versionUtils.getListFromObj(obj, field, false)

        when {
            list != null -> {
                IntStream.range(0, list.size).forEach { i ->
                    val iteratableObj = list[i]
                    val iteratorIdField = iteratableObj?.let { versionUtils.getIteratorIdField(it) }
                    iteratableObj?.let { setIteratorIdOnIterable(failedIteratorApplication, i, it, iteratorIdField!!) }
                }

                setIteratorIds(list)
            }
        }
    }

    @Throws(IllegalAccessException::class)
    private fun <T> setIteratorIdsOnSetField(failedIteratorApplication: AtomicBoolean, obj: T, field: Field) {
        val set = versionUtils.getSetFromObj(obj, field, false)

        when {
            set != null -> {
                val itr = set.iterator()

                var i = 0
                while (itr.hasNext()) {
                    val o = itr.next()
                    val iteratorIdField = o?.let { versionUtils.getIteratorIdField(it) }
                    o?.let { setIteratorIdOnIterable(failedIteratorApplication, i, it, iteratorIdField!!) }

                    i++
                }

                setIteratorIds(set)
            }
        }
    }

    private fun setIteratorIdOnIterable(failedIterator: AtomicBoolean, i: Int, iteratableObj: Any, f: Field) {
        verifyIteratorIdField(f)

        f.isAccessible = true

        try {
            if (f.get(iteratableObj) == null) {
                when (f.type.simpleName) {
                    "String" -> f.set(iteratableObj, "" + i)
                    "Short" -> f.set(iteratableObj, java.lang.Short.parseShort("" + i))
                    "Integer" -> f.set(iteratableObj, Integer.parseInt("" + i))
                    "Long" -> f.set(iteratableObj, java.lang.Long.parseLong("" + i))
                    "Double" -> f.set(iteratableObj, java.lang.Double.parseDouble("" + i))
                    "Float" -> f.set(iteratableObj, java.lang.Float.parseFloat("" + i))
                    "Boolean" -> f.set(iteratableObj, java.lang.Boolean.parseBoolean("" + i))
                    "BigInteger" -> f.set(iteratableObj, BigInteger("" + i))
                    "BigDecimal" -> f.set(iteratableObj, BigDecimal("" + i))
                    else -> f.set(iteratableObj, i)
                }
            }
        } catch (e: Exception) {
            failedIteratorApply(failedIterator, "Error in iteratorId application", e)
        }
    }

    private fun verifyIteratorIdField(iteratorIdField: Field?) {
        if (iteratorIdField == null) throw RuntimeException("IteratorId field is null!")
        if (iteratorIdField.type.isPrimitive) throw RuntimeException("IteratorId field cannot be a primitive!")
    }
}
