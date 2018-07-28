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

import com.fasterxml.jackson.annotation.JsonInclude
import io.vertx.core.logging.LoggerFactory
import java.lang.reflect.Field
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * This class defines an interface for models that operate on etags.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
interface ETagable {
    var etag: String?

    fun generateAndSetEtag(map: MutableMap<String, String>): Map<String, String> {
        val oldTag = etag
        val etagCode = AtomicLong(java.lang.Long.MAX_VALUE)

        Arrays.stream<Field>(javaClass.declaredFields).forEach { field ->
            field.isAccessible = true

            when {
                Collection::class.java.isAssignableFrom(field.type) -> try {
                    val `object` = field.get(this)

                    if (`object` != null) {
                        (`object` as Collection<*>).forEach { o ->
                            val e = o as ETagable
                            val stringStringMap = e.generateAndSetEtag(map)
                            map.putAll(stringStringMap)

                            val innerEtagCode = when {
                                e.etag != null -> e.etag!!.hashCode().toLong()
                                else -> 12345L
                            }

                            etagCode.set(etagCode.get() xor innerEtagCode)
                        }
                    }
                } catch (e: IllegalAccessException) {
                    logger.error("Cannot access collection for etag!", e)
                }
                else -> try {
                    val value = field.get(this)
                    val innerEtagCode = value?.hashCode()?.toLong() ?: 12345L

                    etagCode.set(etagCode.get() xor innerEtagCode)
                } catch (e: IllegalAccessException) {
                    logger.error("Cannot access field for etag!", e)
                }
            }
        }

        val newTag = ModelUtils.returnNewEtag(etagCode.get())

        if (oldTag != null && oldTag == newTag) return map
        etag = newTag
        map[generateEtagKeyIdentifier()] = newTag

        return reGenerateParent(map)
    }

    fun reGenerateParent(map: Map<String, String>): Map<String, String> {
        return map
    }

    fun generateEtagKeyIdentifier(): String

    companion object {
        val logger = LoggerFactory.getLogger(ETagable::class.java.simpleName)
    }
}
