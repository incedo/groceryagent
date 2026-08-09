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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

application {
    mainClass.set("com.groceryautomate.importer.ApplicationKt")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("grocery-catalog-importer")
            mainClass.set("com.groceryautomate.importer.ApplicationKt")
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
