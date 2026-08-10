plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:catalog"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.minio)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(rootProject.tasks.named("lineCountCheck"))
}
