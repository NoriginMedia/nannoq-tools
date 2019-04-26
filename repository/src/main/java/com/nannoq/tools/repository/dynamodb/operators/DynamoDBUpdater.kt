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

package com.nannoq.tools.repository.dynamodb.operators

import com.nannoq.tools.repository.dynamodb.DynamoDBRepository
import com.nannoq.tools.repository.models.Cacheable
import com.nannoq.tools.repository.models.DynamoDBModel
import com.nannoq.tools.repository.models.ETagable
import com.nannoq.tools.repository.models.Model
import com.nannoq.tools.repository.repository.Repository
import com.nannoq.tools.repository.repository.Repository.INCREMENTATION.ADDITION
import com.nannoq.tools.repository.repository.Repository.INCREMENTATION.SUBTRACTION
import io.vertx.core.logging.LoggerFactory
import java.lang.reflect.Field
import java.util.Arrays

/**
 * This class defines the update operations for the DynamoDBRepository.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class DynamoDBUpdater<E>(private val db: DynamoDBRepository<E>)
        where E : ETagable, E : Cacheable, E : DynamoDBModel, E : Model {

    @Throws(IllegalArgumentException::class)
    fun incrementField(record: E, fieldName: String): Boolean {
        val field = db.checkAndGetField(fieldName)

        return try {
            doCrementation(record, field, ADDITION)
        } catch (e: IllegalAccessException) {
            logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

            false
        }
    }

    @Throws(IllegalArgumentException::class)
    fun decrementField(record: E, fieldName: String): Boolean {
        val field = db.checkAndGetField(fieldName)

        return try {
            doCrementation(record, field, SUBTRACTION)
        } catch (e: IllegalAccessException) {
            logger.error(e.toString() + " : " + e.message + " : " + Arrays.toString(e.stackTrace))

            false
        }
    }

    @Throws(IllegalAccessException::class)
    private fun doCrementation(record: E, field: Field, direction: Repository.INCREMENTATION): Boolean {
        when {
            field.type == java.lang.Long::class.java -> {
                when (direction) {
                    ADDITION -> field.set(record, field.get(record) as Long + 1L)
                    SUBTRACTION -> if (field.get(record) as Long != 0L) {
                        field.set(record, field.get(record) as Long - 1L)
                    }
                }

                return true
            }
            field.type == java.lang.Integer::class.java -> {
                when (direction) {
                    ADDITION -> field.set(record, field.get(record) as Int + 1L)
                    SUBTRACTION -> if (field.get(record) as Int != 0) {
                        field.set(record, field.get(record) as Int - 1L)
                    }
                }

                return true
            }
            field.type == java.lang.Short::class.java -> {
                when (direction) {
                    ADDITION -> field.set(record, field.get(record) as Short + 1L)
                    SUBTRACTION -> if ((field.get(record) as Short).toInt() != 0) {
                        field.set(record, field.get(record) as Short - 1L)
                    }
                }

                return true
            }
            field.type == java.lang.Double::class.java -> {
                when (direction) {
                    ADDITION -> field.set(record, field.get(record) as Double + 1L)
                    SUBTRACTION -> if (field.get(record) as Double != 0.0) {
                        field.set(record, field.get(record) as Double - 1L)
                    }
                }

                return true
            }
            field.type == java.lang.Float::class.java -> {
                when (direction) {
                    ADDITION -> field.set(record, field.get(record) as Float + 1L)
                    SUBTRACTION -> if ((field.get(record) as Float).toDouble() != 0.0) {
                        field.set(record, field.get(record) as Float - 1L)
                    }
                }

                return true
            }
            field.type == java.lang.Long::class.javaPrimitiveType -> {
                when (direction) {
                    ADDITION -> field.setLong(record, field.getLong(record) + 1L)
                    SUBTRACTION -> if (field.getLong(record) != 0L) {
                        field.setLong(record, field.getLong(record) - 1L)
                    }
                }

                return true
            }
            field.type == java.lang.Integer::class.javaPrimitiveType -> {
                when (direction) {
                    ADDITION -> field.setInt(record, field.getInt(record) + 1)
                    SUBTRACTION -> if (field.getInt(record) != 0) {
                        field.setInt(record, field.getInt(record) - 1)
                    }
                }

                return true
            }
            field.type == java.lang.Short::class.javaPrimitiveType -> {
                var value = field.getShort(record)

                when (direction) {
                    ADDITION -> field.setShort(record, ++value)
                    SUBTRACTION -> if (value.toInt() != 0) {
                        field.setShort(record, --value)
                    }
                }

                return true
            }
            field.type == java.lang.Double::class.javaPrimitiveType -> {
                when (direction) {
                    ADDITION -> field.setDouble(record, field.getDouble(record) + 1.0)
                    SUBTRACTION -> if (field.getDouble(record) != 0.0) {
                        field.setDouble(record, field.getDouble(record) - 1.0)
                    }
                }

                return true
            }
            field.type == java.lang.Float::class.javaPrimitiveType -> {
                when (direction) {
                    ADDITION -> field.setFloat(record, field.getFloat(record) + 1.0f)
                    SUBTRACTION -> if (field.getFloat(record) != 0f) {
                        field.setFloat(record, field.getFloat(record) - 1.0f)
                    }
                }

                return true
            }
            else -> return false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DynamoDBUpdater::class.java.simpleName)
    }
}
