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

package com.nannoq.tools.cluster

import com.hazelcast.core.Hazelcast
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBusOptions
import io.vertx.core.http.ClientAuth
import io.vertx.core.logging.LoggerFactory
import io.vertx.core.net.JksOptions
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.IntStream
import java.util.zip.ZipInputStream
import org.apache.commons.io.IOUtils

/**
 * This class defines helpers for operating the cluster. It can produce a member list, sets eventbus SSL, and produces
 * the modified cluster.xml file as a result of port scanning.
 *
 * @author Anders Mikkelsen
 * @version 17.11.2017
 */
object ClusterUtils {
    private val logger = LoggerFactory.getLogger(ClusterUtils::class.java.simpleName)

    fun clusterReport(aLong: Long?) {
        val vertx = Vertx.currentContext().owner()

        when {
            vertx.isClustered -> {
                val sb = StringBuilder()
                sb.append("Cluster Members:\n")
                sb.append("----------------\n")

                val instances = Hazelcast.getAllHazelcastInstances()
                instances.stream().findFirst().ifPresent {
                    it.cluster.members.stream()
                            .map { member ->
                                member.socketAddress.address.toString() + ":" + member.socketAddress.port
                            }
                            .forEach { name -> sb.append(name).append("\n") }
                }

                sb.append("----------------")

                logger.info(sb.toString())
            }
            else -> logger.error("Vertx is not clustered!")
        }
    }

    fun setSSLEventBus(keystoreName: String, keyStoreKey: String, eventBusOptions: EventBusOptions): EventBusOptions {
        eventBusOptions.setSsl(true)
                .setKeyStoreOptions(JksOptions().setPath(keystoreName).setPassword(keyStoreKey))
                .setTrustStoreOptions(JksOptions().setPath(keystoreName).setPassword(keyStoreKey))
                .clientAuth = ClientAuth.REQUIRED

        return eventBusOptions
    }

    fun createModifiedClusterConfigByPortScanning(
        subnetBase: String,
        thirdElementScanRange: Int,
        clusterConfigFileName: String
    ) {
        val contents = readClusterConfig(clusterConfigFileName)
                ?: throw IllegalArgumentException("Could not load cluster config!")

        setClusterMembersForSubnet(subnetBase, thirdElementScanRange, contents, true)
    }

    fun createModifiedClusterConfigByPortScanning(
        subnetBase: String,
        thirdElementScanRange: Int,
        clusterConfigFileName: String,
        dev: Boolean
    ) {
        val contents = readClusterConfig(clusterConfigFileName)
                ?: throw IllegalArgumentException("Could not load cluster config!")

        setClusterMembersForSubnet(subnetBase, thirdElementScanRange, contents, dev)
    }

    private fun readClusterConfig(clusterConfigFileName: String): String? {
        var fileContents: String? = null

        try {
            val src = ClusterUtils::class.java.protectionDomain.codeSource

            when {
                src != null -> {
                    val jar = src.location
                    val zip = ZipInputStream(jar.openStream())

                    while (true) {
                        val e = zip.nextEntry ?: break

                        if (e.name.equals(clusterConfigFileName, ignoreCase = true)) {
                            fileContents = readZipInputStream(zip)

                            break
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return fileContents
    }

    @Throws(IOException::class)
    private fun readZipInputStream(stream: ZipInputStream): String {
        val sb = StringBuilder()
        val lines = IOUtils.readLines(stream)
        lines.forEach(Consumer<String> { sb.append(it) })

        return sb.toString()
    }

    private fun setClusterMembersForSubnet(subnetBase: String, thirdElement: Int, contents: String, dev: Boolean) {
        try {
            val scans = AtomicInteger()
            val scansComplete = AtomicInteger()
            val replacer = StringBuilder()
            val CLUSTER_MEMBER_LIST = CopyOnWriteArrayList<String>()

            val executorService = Executors.newCachedThreadPool()

            println("Initializing Port Scan!")

            val portScanners = CopyOnWriteArrayList<Runnable>()

            IntStream.rangeClosed(0, thirdElement).parallel().forEach { baseIpInt ->
                IntStream.rangeClosed(0, 254).parallel().forEach { lastIpInt ->
                    portScanners.add(Runnable {
                        println("Now running scan for: $baseIpInt.$lastIpInt")

                        when {
                            CLUSTER_MEMBER_LIST.size == 0 -> {
                                scans.incrementAndGet()

                                try {
                                    Socket().use { socket ->
                                        socket.connect(
                                                InetSocketAddress("$subnetBase$baseIpInt.$lastIpInt", 5701), 2000)

                                        if (socket.isConnected) {
                                            CLUSTER_MEMBER_LIST.add("<member>" + subnetBase + baseIpInt + "." +
                                                    lastIpInt + "</member>")

                                            println("Member detected at " + subnetBase + baseIpInt + "." +
                                                    lastIpInt)
                                        }
                                    }
                                } catch (e: IOException) {
                                    logger.trace("No connection on: $subnetBase$baseIpInt.$lastIpInt")
                                }

                                scansComplete.incrementAndGet()
                            }
                        }
                    })
                }
            }

            portScanners.forEach(Consumer<Runnable> { executorService.submit(it) })

            executorService.shutdown()

            while (!executorService.isTerminated) {
                Thread.sleep(2000L)

                println("Scan completion Status: (" + scansComplete.get() + "/" + scans.get() + ")")

                if (CLUSTER_MEMBER_LIST.size > 0) {
                    println("Scan found at least one member, killing all scanners!")

                    executorService.shutdownNow()

                    break
                }
            }

            var clusterConfig = contents

            when {
                CLUSTER_MEMBER_LIST.size > 0 -> {
                    CLUSTER_MEMBER_LIST.subList(0, CLUSTER_MEMBER_LIST.size - 1).forEach { member -> replacer.append(member).append("\n") }
                    replacer.append(CLUSTER_MEMBER_LIST[CLUSTER_MEMBER_LIST.size - 1])
                    val memberOverview = replacer.toString()

                    println("Finalized Port Scan. Members at: $memberOverview")

                    clusterConfig = if (dev) {
                        contents.replace("<interface>" + subnetBase + "0.0-15</interface>", memberOverview)
                    } else {
                        contents.replace("<interface>" + subnetBase + "0.*</interface>", memberOverview)
                    }
                }
                else -> println("Skipping memberlist due to it being empty, doing broad sweep...")
            }

            val dir = Paths.get("/usr/verticles/cluster-modified.xml")
            val file = dir.toFile()

            file.createNewFile()

            file.setWritable(true)

            BufferedWriter(OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8)).use {
                it.write(clusterConfig)
                it.flush()
                it.close()
            }
        } catch (e: IOException) {
            logger.error("Error in finding other services!", e)
        } catch (e: InterruptedException) {
            logger.error("Error in finding other services!", e)
        }
    }
}
