object Versions {
    const val jvmTargetValue = "1.8"
    const val vertx_version = "3.8.2"
    const val vertx_redis_version = "3.6.3"
    const val kotlin_version = "1.3.50"
    const val dokka_version = "0.10.0"
    const val log4j_version = "2.12.1"
    const val junit_version = "5.5.2"
    const val assertj_version = "3.13.2"
    const val com_lmax_version = "3.4.2"
    const val rest_assured_version = "4.1.2"
    const val awssdk_version = "1.11.539"
    const val awssdk_encryption_version = "1.13.0"
    const val imagei_version = "3.4.2"
    const val jackson_annotations_version = "2.10.0"
    const val commons_validator_version = "1.6"
    const val google_findbugs_version = "3.0.1"
    const val jsoup_version = "1.12.1"
    const val embedded_redis_version = "0.6"
    const val jjwt_version = "0.9.1"
    const val google_api_client_version = "1.30.4"
    const val facebook4j_version = "2.4.13"
    const val jInstagram_version = "1.2.2"
    const val nexus_staging_version = "0.21.1"
    const val google_guava_jdk5_version = "17.0"
    const val smack_version = "3.2.1"
    const val s3mock_version = "0.2.5"
    const val sqlLite_version = "1.0.392"
    const val metadata_extractor_version = "2.11.0"
    const val tika_version = "1.20"
    const val dynamodb_local_version = "[1.11.477,2.0]"
    const val apache_commons_io_version = "2.6"
    const val apache_commons_lang_version = "3.9"
    const val jcache_version = "1.1.1"
    const val gradle_test_logger_version = "2.0.0"
    const val gradle_versions_version = "0.26.0"
    const val gradle_gitflow_version = "0.6.0"
    const val gradle_ktlint_version = "9.0.0"
    const val logger_factory_version = "io.vertx.core.logging.Log4j2LogDelegateFactory"
}

object Libs {
    // Vert.x
    const val vertx_core  = "io.vertx:vertx-core:${Versions.vertx_version}"
    const val vertx_web  = "io.vertx:vertx-web:${Versions.vertx_version}"
    const val vertx_hazelcast  = "io.vertx:vertx-hazelcast:${Versions.vertx_version}"
    const val vertx_codegen  = "io.vertx:vertx-codegen:${Versions.vertx_version}"
    const val vertx_lang_js  = "io.vertx:vertx-lang-js:${Versions.vertx_version}"
    const val vertx_lang_ruby  = "io.vertx:vertx-lang-ruby:${Versions.vertx_version}"
    const val vertx_lang_kotlin  = "io.vertx:vertx-lang-kotlin:${Versions.vertx_version}"
    const val vertx_service_proxy  = "io.vertx:vertx-service-proxy:${Versions.vertx_version}"
    const val vertx_service_discovery  = "io.vertx:vertx-service-discovery:${Versions.vertx_version}"
    const val vertx_sockjs_service_proxy  = "io.vertx:vertx-sockjs-service-proxy:${Versions.vertx_version}"
    const val vertx_circuit_breaker  = "io.vertx:vertx-circuit-breaker:${Versions.vertx_version}"
    const val vertx_redis_client  = "io.vertx:vertx-redis-client:${Versions.vertx_redis_version}"
    const val vertx_mail_client  = "io.vertx:vertx-mail-client:${Versions.vertx_version}"
    const val vertx_lang_kotlin_coroutines  = "io.vertx:vertx-lang-kotlin-coroutines:${Versions.vertx_version}"
    
    // AWS
    const val aws_core = "com.amazonaws:aws-java-sdk-core:${Versions.awssdk_version}"
    const val aws_sts = "com.amazonaws:aws-java-sdk-sts:${Versions.awssdk_version}"
    const val aws_dynamodb = "com.amazonaws:aws-java-sdk-dynamodb:${Versions.awssdk_version}"
    const val aws_cloudfront = "com.amazonaws:aws-java-sdk-cloudfront:${Versions.awssdk_version}"
    const val aws_encryption = "com.amazonaws:aws-dynamodb-encryption-java:${Versions.awssdk_encryption_version}"

    // Image And File Detection
    const val imageio_core = "com.twelvemonkeys.imageio:imageio-core:${Versions.imagei_version}"
    const val imageio_jpeg = "com.twelvemonkeys.imageio:imageio-jpeg:${Versions.imagei_version}"
    const val imageio_tiff = "com.twelvemonkeys.imageio:imageio-tiff:${Versions.imagei_version}"
    const val imageio_psd = "com.twelvemonkeys.imageio:imageio-psd:${Versions.imagei_version}"
    const val imageio_metadata = "com.twelvemonkeys.imageio:imageio-metadata:${Versions.imagei_version}"
    const val imageio_pnm = "com.twelvemonkeys.imageio:imageio-pnm:${Versions.imagei_version}"
    const val imageio_icns = "com.twelvemonkeys.imageio:imageio-icns:${Versions.imagei_version}"
    const val imageio_pdf = "com.twelvemonkeys.imageio:imageio-pdf:${Versions.imagei_version}"
    const val imageio_pcx = "com.twelvemonkeys.imageio:imageio-pcx:${Versions.imagei_version}"
    const val imageio_sgi = "com.twelvemonkeys.imageio:imageio-sgi:${Versions.imagei_version}"
    const val imageio_iff = "com.twelvemonkeys.imageio:imageio-iff:${Versions.imagei_version}"
    const val imageio_tga = "com.twelvemonkeys.imageio:imageio-tga:${Versions.imagei_version}"
    const val imageio_pict = "com.twelvemonkeys.imageio:imageio-pict:${Versions.imagei_version}"
    const val imageio_batik = "com.twelvemonkeys.imageio:imageio-batik:${Versions.imagei_version}"
    const val metadata_extractor = "com.drewnoakes:metadata-extractor:${Versions.metadata_extractor_version}"
    const val tika_core = "org.apache.tika:tika-core:${Versions.tika_version}"
    
    // Sanitation
    const val jsoup = "org.jsoup:jsoup:${Versions.jsoup_version}"

    // XMPP
    const val smack = "org.igniterealtime.smack:smack:${Versions.smack_version}"

    // Auth
    const val jjwt = "io.jsonwebtoken:jjwt:${Versions.jjwt_version}"
    const val google_api_client = "com.google.api-client:google-api-client:${Versions.google_api_client_version}"
    const val facebook4j = "org.facebook4j:facebook4j-core:${Versions.facebook4j_version}"
    const val jInstagram = "com.sachinhandiekar:jInstagram:${Versions.jInstagram_version}"

    // Caching
    const val jcache = "javax.cache:cache-api:${Versions.jcache_version}"

    // Jackson
    const val jackson_annotations = "com.fasterxml.jackson.core:jackson-annotations:${Versions.jackson_annotations_version}"

    // Commons
    const val commons_lang3  = "org.apache.commons:commons-lang3:${Versions.apache_commons_lang_version}"
    const val commons_io  = "commons-io:commons-io:${Versions.apache_commons_io_version}"
    const val commons_validator  = "commons-validator:commons-validator:${Versions.commons_validator_version}"
    const val findbugs_annotations  = "com.google.code.findbugs:annotations:${Versions.google_findbugs_version}"
    const val guava_jdk5 = "com.google.guava:guava-jdk5:${Versions.google_guava_jdk5_version}"

    // Test
    const val vertx_config = "io.vertx:vertx-config:${Versions.vertx_version}"
    const val vertx_junit5 = "io.vertx:vertx-junit5:${Versions.vertx_version}"
    const val assertj_core = "org.assertj:assertj-core:${Versions.assertj_version}"
    const val rest_assured = "io.rest-assured:rest-assured:${Versions.rest_assured_version}"
    const val rest_assured_json_path = "io.rest-assured:json-path:${Versions.rest_assured_version}"
    const val rest_assured_json_schema_validator = "io.rest-assured:json-schema-validator:${Versions.rest_assured_version}"
    const val junit_jupiter_engine = "org.junit.jupiter:junit-jupiter-engine:${Versions.junit_version}"

    // Test Redis
    const val embedded_redis = "com.github.kstyrc:embedded-redis:${Versions.embedded_redis_version}"

    // DynamoDb Test
    const val dynamodb_local = "com.amazonaws:DynamoDBLocal:${Versions.dynamodb_local_version}"
    const val sqlite4 = "com.almworks.sqlite4java:sqlite4java:${Versions.sqlLite_version}"
    const val sqlite4_win32_x86 = "com.almworks.sqlite4java:sqlite4java-win32-x86:${Versions.sqlLite_version}"
    const val sqlite4_win32_x64 = "com.almworks.sqlite4java:sqlite4java-win32-x64:${Versions.sqlLite_version}"
    const val sqlite4_osx = "com.almworks.sqlite4java:libsqlite4java-osx:${Versions.sqlLite_version}"
    const val sqlite4_linux_i386 = "com.almworks.sqlite4java:libsqlite4java-linux-i386:${Versions.sqlLite_version}"
    const val sqlite4_linux_amd64 = "com.almworks.sqlite4java:libsqlite4java-linux-amd64:${Versions.sqlLite_version}"

    // S3 Test
    const val s3mock = "io.findify:s3mock_2.12:${Versions.s3mock_version}"
}