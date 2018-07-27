package com.nannoq.tools.web.controllers.utils

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