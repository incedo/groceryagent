plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:catalog"))
    implementation(project(":integration:picnic-client"))
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}

application {
    mainClass.set("com.groceryautomate.backend.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(rootProject.tasks.named("lineCountCheck"))
}
