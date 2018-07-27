package com.nannoq.tools.repository.dynamodb.utils

import io.restassured.specification.RequestSender
import io.restassured.specification.RequestSpecification
import io.restassured.specification.ResponseSpecification

interface RestAssuredFix {
    fun RequestSpecification.When(): RequestSpecification {
        return this.`when`()
    }

    fun ResponseSpecification.When(): RequestSender {
        return this.`when`()
    }
}