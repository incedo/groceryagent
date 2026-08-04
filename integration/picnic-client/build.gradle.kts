import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:catalog"))
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.java)
        }
    }
}

val picnicLiveSmokeMain =
    "com.groceryautomate.picnic.live.PicnicLiveSmokeMainKt"

tasks.register<JavaExec>("picnicLiveSmoke") {
    group = "verification"
    description = "Runs an opt-in, read-only Picnic search and product-detail smoke test."
    workingDir(rootProject.projectDir)
    val testCompilation = kotlin.targets
        .getByName<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget>("jvm")
        .compilations.getByName("test")
    dependsOn(testCompilation.compileTaskProvider)
    classpath(testCompilation.output.allOutputs, testCompilation.runtimeDependencyFiles)
    mainClass.set(picnicLiveSmokeMain)
    val envFile = providers.gradleProperty("picnicEnvFile").orElse(".env.picnic.local")
    val query = providers.gradleProperty("picnicQuery").orElse("pasta")
    val productId = providers.gradleProperty("picnicProductId")
    doFirst {
        systemProperty("picnic.env.file", envFile.get())
        systemProperty("picnic.query", query.get())
        productId.orNull?.let { systemProperty("picnic.product.id", it) }
    }
}

kover {
    reports {
        total {
            html {
                onCheck = true
            }
            xml {
                onCheck = true
            }
            verify {
                rule("Minimum line coverage") {
                    minBound(95)
                }
                rule("Minimum branch coverage") {
                    minBound(64, CoverageUnit.BRANCH)
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(rootProject.tasks.named("lineCountCheck"))
    dependsOn(tasks.named("koverVerify"))
}
