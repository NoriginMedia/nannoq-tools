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

import java.security.NoSuchAlgorithmException

/**
 * This class defines helpers for Model operations.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
object ModelUtils {
    @Throws(NoSuchAlgorithmException::class)
    fun hashString(stringToHash: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        digest.update(stringToHash.toByteArray())
        val messageDigest = digest.digest()

        val hexString = StringBuilder()

        for (aMessageDigest in messageDigest) {
            hexString.append(Integer.toHexString(0xFF and aMessageDigest.toInt()))
        }

        return hexString.toString()
    }

    fun returnNewEtag(tag: Long): String {
        try {
            return ModelUtils.hashString(tag.toString())
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()

            return tag.toString()
        }

    }
}
