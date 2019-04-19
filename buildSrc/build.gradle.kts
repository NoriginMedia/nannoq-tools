plugins {
    `kotlin-dsl`
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

repositories {
    jcenter()
}

tasks {
    val jar by existing(Jar::class) {
        enabled = false
    }
}