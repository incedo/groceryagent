plugins {
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:catalog"))
    implementation(project(":core:events"))
    implementation(project(":integration:picnic-client"))
    implementation(project(":integration:postgres"))
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

application {
    mainClass.set("com.groceryautomate.backend.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("grocery-catalog-service")
            mainClass.set("com.groceryautomate.backend.ApplicationKt")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            resources.autodetect()
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}

tasks.named("check") {
    dependsOn(rootProject.tasks.named("lineCountCheck"))
}
