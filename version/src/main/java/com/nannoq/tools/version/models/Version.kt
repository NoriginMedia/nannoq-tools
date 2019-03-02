package com.nannoq.tools.version.models

import java.time.Instant

class Version(var id: Long? = null, var correlationId: String? = null, val createdAt: Instant = Instant.now(), val objectModificationMap: Map<String, ObjectModification> = mapOf())
