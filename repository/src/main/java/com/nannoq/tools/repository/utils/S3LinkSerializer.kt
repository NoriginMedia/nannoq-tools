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

package com.nannoq.tools.repository.utils

import com.amazonaws.services.dynamodbv2.datamodeling.S3Link
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

import java.io.IOException

/**
 * This class defines an serializer for Jackson for the S3Link class.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class S3LinkSerializer : StdSerializer<S3Link>(S3Link::class.java) {

    @Throws(IOException::class)
    override fun serialize(
        s3Link: S3Link,
        jsonGenerator: JsonGenerator,
        serializerProvider: SerializerProvider
    ) {
        jsonGenerator.writeStartObject()
        jsonGenerator.writeObjectFieldStart("s3")
        jsonGenerator.writeStringField("bucket", s3Link.bucketName)
        jsonGenerator.writeStringField("key", s3Link.key)
        jsonGenerator.writeStringField("region", s3Link.s3Region.firstRegionId)
        jsonGenerator.writeEndObject()
        jsonGenerator.writeEndObject()
    }
}
