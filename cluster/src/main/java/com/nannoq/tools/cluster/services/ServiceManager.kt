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

package com.nannoq.tools.cluster.services

import io.vertx.codegen.annotations.Fluent
import io.vertx.core.*
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.http.HttpClient
import io.vertx.core.impl.ConcurrentHashSet
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.servicediscovery.Record
import io.vertx.servicediscovery.ServiceDiscovery
import io.vertx.servicediscovery.ServiceDiscoveryOptions
import io.vertx.servicediscovery.types.EventBusService
import io.vertx.servicediscovery.types.HttpEndpoint
import io.vertx.serviceproxy.ServiceBinder
import io.vertx.serviceproxy.ServiceException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * This class defines a wrapper for publishing and consuming service declaration interfaces, and HTTP records.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
class ServiceManager {

    private var serviceDiscovery: ServiceDiscovery? = null
    private val registeredServices = ConcurrentHashMap<String, MessageConsumer<JsonObject>>()
    private val registeredRecords = ConcurrentHashMap<String, Record>()
    private val fetchedServices = ConcurrentHashMap<String, ConcurrentHashSet<Any>>()

    private var vertx: Vertx? = null
    private var serviceAnnounceConsumer: MessageConsumer<JsonObject>? = null

    private constructor() {
        throw IllegalArgumentException("Should never run!")
    }

    private constructor(vertx: Vertx) {
        this.vertx = vertx
        openDiscovery()
        startServiceManagerKillVerticle()
    }

    private fun startServiceManagerKillVerticle() {
        vertx?.deployVerticle(KillVerticle())
    }

    private inner class KillVerticle : AbstractVerticle() {
        @Throws(Exception::class)
        override fun stop(stopFuture: Future<Void>) {
            logger.info("Destroying ServiceManager")

            if (serviceDiscovery != null) {
                logger.info("Unpublishing all records...")

                val unPublishFutures = ArrayList<Future<*>>()

                registeredRecords.forEach { _, v ->
                    val unpublish = Future.future<Boolean>()

                    serviceDiscovery!!.unpublish(v.registration) {
                        if (it.failed()) {
                            logger.info("Failed Unpublish: " + v.name, it.cause())

                            unpublish.fail(it.cause())
                        } else {
                            logger.info("Unpublished: " + v.name)

                            unpublish.complete()
                        }
                    }

                    unPublishFutures.add(unpublish)
                }

                CompositeFuture.all(unPublishFutures).setHandler {
                    try {
                        registeredRecords.clear()

                        logger.info("UnPublish complete, Unregistering all services...")

                        registeredServices.forEach { _, v ->
                            ServiceBinder(vertx).setAddress(v.address()).unregister(v)

                            logger.info("Unregistering " + v.address())
                        }

                        registeredServices.clear()

                        logger.info("Releasing all consumed service objects...")

                        fetchedServices.values.forEach { ServiceDiscovery.releaseServiceObject(serviceDiscovery!!, it) }

                        fetchedServices.clear()

                        closeDiscovery(Handler {
                            serviceAnnounceConsumer = null

                            logger.info("Discovery Closed!")

                            instanceMap.remove(vertx)
                            stopFuture.tryComplete()

                            logger.info("ServiceManager destroyed...")
                        })
                    } finally {
                        instanceMap.remove(vertx)
                        stopFuture.tryComplete()

                        logger.info("ServiceManager destroyed...")
                    }
                }
            } else {
                logger.info("Discovery is null...")

                instanceMap.remove(vertx)
                stopFuture.tryComplete()
            }
        }
    }

    private fun openDiscovery() {
        logger.debug("Opening Discovery...")

        if (serviceDiscovery == null) {
            serviceDiscovery = ServiceDiscovery.create(vertx, ServiceDiscoveryOptions()
                    .setAnnounceAddress(NANNOQ_SERVICE_ANNOUNCE_ADDRESS)
                    .setUsageAddress(NANNOQ_SERVICE_ANNOUNCE_ADDRESS)
                    .setName(NANNOQ_SERVICE_SERVICE_NAME))

            logger.debug("Setting Discovery message consumer...")

            serviceAnnounceConsumer = vertx?.eventBus()
                    ?.consumer(NANNOQ_SERVICE_ANNOUNCE_ADDRESS) { this.handleServiceEvent(it) }
        }

        logger.debug("Discovery ready...")
    }

    private fun handleServiceEvent(serviceEvent: Message<JsonObject>) {
        val headers = serviceEvent.headers()
        val body = serviceEvent.body()

        logger.trace("Service Event:\n" + Json.encodePrettily(serviceEvent) +
                "\nHeaders:\n" + Json.encodePrettily(headers) +
                "\nBody:\n" + Json.encodePrettily(body))

        val name = body.getString("name")
        val status = body.getString("status")

        if (status != null && status == "DOWN") {
            logger.debug("Removing downed service: $name")

            fetchedServices.remove(name)
        }
    }

    private fun closeDiscovery(resultHandler: Handler<AsyncResult<Void>>) {
        if (serviceDiscovery != null) serviceDiscovery!!.close()
        serviceDiscovery = null

        logger.debug("Unregistering Service Event Listener...")

        if (serviceAnnounceConsumer != null) serviceAnnounceConsumer!!.unregister(resultHandler)
    }

    @Fluent
    fun publishApi(httpRecord: Record): ServiceManager {
        return publishService(httpRecord, Consumer {
            registeredRecords.put(it.getRegistration(), it)
        }, Handler { this.handlePublishResult(it) })
    }

    @Fluent
    fun publishApi(httpRecord: Record,
                   resultHandler: Handler<AsyncResult<Record>>): ServiceManager {
        return publishService(httpRecord, Consumer { registeredRecords.put(it.getRegistration(), it) }, resultHandler)
    }

    @Fluent
    fun unPublishApi(service: Record, resultHandler: Handler<AsyncResult<Void>>): ServiceManager {
        registeredRecords.remove(service.registration)
        serviceDiscovery!!.unpublish(service.registration, resultHandler)
        val objects = fetchedServices[service.name]

        if (objects != null && objects.size > 0) {
            val iterator = objects.iterator()
            iterator.next()
            iterator.remove()
        }

        return this
    }

    @Fluent
    fun <T> publishService(type: Class<T>, service: T): ServiceManager {
        val serviceName = type.simpleName

        return publishService(createRecord(serviceName, type), Consumer {
            registeredServices.put(it.getRegistration(),
                    ServiceBinder(vertx)
                            .setTimeoutSeconds(NANNOQ_SERVICE_DEFAULT_TIMEOUT.toLong())
                            .setAddress(serviceName)
                            .register(type, service))
        }, Handler { this.handlePublishResult(it) })
    }

    @Fluent
    fun <T> publishService(type: Class<T>, customName: String, service: T): ServiceManager {
        return publishService(createRecord(customName, type), Consumer {
            registeredServices.put(it.getRegistration(),
                    ServiceBinder(vertx)
                            .setTimeoutSeconds(NANNOQ_SERVICE_DEFAULT_TIMEOUT.toLong())
                            .setAddress(customName)
                            .register(type, service))
        }, Handler { this.handlePublishResult(it) })
    }

    @Fluent
    fun <T> publishService(type: Class<T>, service: T,
                           resultHandler: Handler<AsyncResult<Record>>): ServiceManager {
        return publishService(createRecord(type), Consumer {
            registeredServices.put(it.getRegistration(),
                    ServiceBinder(vertx)
                            .setTimeoutSeconds(NANNOQ_SERVICE_DEFAULT_TIMEOUT.toLong())
                            .setAddress(type.simpleName)
                            .register(type, service))
        }, resultHandler)
    }

    @Fluent
    fun <T> publishService(type: Class<T>, customName: String, service: T,
                           resultHandler: Handler<AsyncResult<Record>>): ServiceManager {
        return publishService(createRecord(customName, type), Consumer {
            registeredServices.put(it.getRegistration(),
                    ServiceBinder(vertx)
                            .setTimeoutSeconds(NANNOQ_SERVICE_DEFAULT_TIMEOUT.toLong())
                            .setAddress(customName)
                            .register(type, service))
        }, resultHandler)
    }

    @Fluent
    fun <T> unPublishService(type: Class<T>, service: Record): ServiceManager {
        val serviceName = type.simpleName

        return unPublishService(serviceName, service)
    }

    @Fluent
    fun <T> unPublishService(type: Class<T>, service: Record,
                             resultHandler: Handler<AsyncResult<Void>>): ServiceManager {
        val serviceName = type.simpleName

        return unPublishService(serviceName, service, resultHandler)
    }

    @Fluent
    @JvmOverloads
    fun unPublishService(serviceName: String, service: Record,
                         resultHandler: Handler<AsyncResult<Void>> = Handler {
                             logger.info("Unpublish res: " + it.succeeded())
                         }): ServiceManager {
        ServiceBinder(vertx)
                .setAddress(serviceName)
                .unregister(registeredServices[service.registration])

        serviceDiscovery!!.unpublish(service.registration, resultHandler)

        registeredServices.remove(service.registration)

        val objects = fetchedServices[service.name]

        if (objects != null && objects.size > 0) {
            val iterator = objects.iterator()
            iterator.next()
            iterator.remove()
        }

        return this
    }

    @Fluent
    fun consumeApi(name: String,
                   resultHandler: Handler<AsyncResult<HttpClient>>): ServiceManager {
        return getApi(name, resultHandler)
    }

    @Fluent
    fun <T> consumeService(type: Class<T>, resultHandler: Handler<AsyncResult<T>>): ServiceManager {
        return consumeService(type, type.simpleName, resultHandler)
    }

    @Fluent
    fun <T> consumeService(type: Class<T>, customName: String,
                           resultHandler: Handler<AsyncResult<T>>): ServiceManager {
        return getService(type, customName, resultHandler)
    }

    private fun getApi(name: String, resultHandler: Handler<AsyncResult<HttpClient>>): ServiceManager {
        logger.debug("Getting API: $name")

        val existingServices = fetchedServices[name]

        if (existingServices != null && existingServices.size > 0) {
            logger.debug("Returning fetched Api...")

            val objects = ArrayList(existingServices)
            objects.shuffle()

            resultHandler.handle(Future.succeededFuture(objects[0] as HttpClient))
        } else {
            HttpEndpoint.getClient(serviceDiscovery!!, JsonObject().put("name", name)) { ar ->
                if (ar.failed()) {
                    logger.error("Unable to fetch API...")

                    resultHandler.handle(ServiceException.fail(404, "API not found..."))
                } else {
                    val client = ar.result()
                    var objects: ConcurrentHashSet<Any>? = fetchedServices[name]

                    if (objects == null) {
                        fetchedServices[name] = ConcurrentHashSet()
                        objects = fetchedServices[name]
                    }

                    if (!objects!!.contains(client)) {
                        objects.add(client)
                    }

                    fetchedServices[name] = objects

                    resultHandler.handle(Future.succeededFuture(client))
                }
            }
        }

        return this
    }

    private fun <T> getService(type: Class<T>, resultHandler: Handler<AsyncResult<T>>): ServiceManager {
        return getService(type, type.simpleName, resultHandler)
    }

    private fun <T> getService(type: Class<T>, serviceName: String, resultHandler: Handler<AsyncResult<T>>): ServiceManager {
        logger.debug("Getting service: $serviceName")

        val existingServices = fetchedServices[serviceName]

        if (existingServices != null && existingServices.size > 0) {
            logger.debug("Returning fetched Api...")

            val objects = ArrayList(existingServices)
            objects.shuffle()

            @Suppress("UNCHECKED_CAST")
            resultHandler.handle(Future.succeededFuture(objects[0] as T))
        } else {
            EventBusService.getProxy(serviceDiscovery!!, type) { ar ->
                if (ar.failed()) {
                    logger.error("ERROR: Unable to get service for $serviceName")

                    resultHandler.handle(ServiceException.fail(NOT_FOUND,
                            "Unable to get service for " + serviceName + " : " + ar.cause()))
                } else {
                    val service = ar.result()
                    var objects: ConcurrentHashSet<Any>? = fetchedServices[serviceName]

                    if (objects == null) {
                        fetchedServices[serviceName] = ConcurrentHashSet()
                        objects = fetchedServices[serviceName]
                    }

                    if (!objects!!.contains(service)) {
                        objects.add(service)
                    }

                    fetchedServices[serviceName] = objects

                    logger.debug("Successful fetch of: " + service.toString())

                    resultHandler.handle(Future.succeededFuture(service))
                }
            }
        }

        return this
    }

    private fun <T> createRecord(type: Class<T>): Record {
        return createRecord(type.simpleName, type)
    }

    private fun <T> createRecord(serviceName: String, type: Class<T>): Record {
        return EventBusService.createRecord(serviceName, serviceName, type)
    }

    private fun publishService(record: Record, recordLogic: Consumer<Record>,
                               resultHandler: Handler<AsyncResult<Record>>): ServiceManager {
        serviceDiscovery!!.publish(record) { ar ->
            if (ar.failed()) {
                logger.error("ERROR: Failed publish of " +
                        record.name + " to " +
                        record.location.encodePrettily() + " with " +
                        record.type + " : " +
                        record.status)

                resultHandler.handle(ServiceException.fail(INTERNAL_ERROR, ar.cause().message))
            } else {
                val publishedRecord = ar.result()
                registeredRecords[publishedRecord.registration] = publishedRecord
                recordLogic.accept(publishedRecord)

                logger.debug("Successful publish of: " +
                        publishedRecord.name + " to " +
                        publishedRecord.location.encodePrettily() + " with " +
                        publishedRecord.type + " : " +
                        publishedRecord.status)

                resultHandler.handle(Future.succeededFuture(publishedRecord))
            }
        }

        return this
    }

    private fun handlePublishResult(publishResult: AsyncResult<Record>) {
        if (publishResult.failed()) {
            if (publishResult.cause() is ServiceException) {
                val serviceException = publishResult.cause() as ServiceException

                logger.error("Unable to publish service: " +
                        serviceException.failureCode() + " : " +
                        serviceException.message)
            } else {
                logger.error("Unable to publish service: " + publishResult.cause())
            }
        } else {
            val record = publishResult.result()

            logger.debug("Published Service Record: " +
                    record.name + " : " +
                    record.location + " : " +
                    record.type + " : " +
                    record.status)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceManager::class.java.simpleName)

        private const val NANNOQ_SERVICE_ANNOUNCE_ADDRESS = "com.nannoq.services.manager.announce"
        private const val NANNOQ_SERVICE_SERVICE_NAME = "nannoq-service-manager-service-discovery"
        private const val NANNOQ_SERVICE_DEFAULT_TIMEOUT = 5

        private const val NOT_FOUND = 404
        private const val INTERNAL_ERROR = 500
        private val instanceMap = mutableMapOf<Vertx, ServiceManager>()

        private lateinit var _instance: ServiceManager

        fun getInstance(): ServiceManager {
            return getInstance(Vertx.currentContext().owner())
        }

        fun getInstance(vertx: Vertx): ServiceManager {
            val instance: ServiceManager? = instanceMap[vertx]

            if (instance != null) return instance

            _instance = ServiceManager(vertx)

            instanceMap[vertx] = _instance

            return _instance
        }

        fun handleResultFailed(t: Throwable) {
            if (t is ServiceException) {
                logger.error(t.failureCode().toString() + " : " +
                        t.message, t)
            } else {
                logger.error(t.message, t)
            }
        }
    }
}
