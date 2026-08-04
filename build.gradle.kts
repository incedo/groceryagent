plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register("lineCountCheck") {
    group = "verification"
    description = "Fails when Kotlin or Gradle source files exceed 300 lines."
    notCompatibleWithConfigurationCache("Scans the live repository source tree.")

    doLast {
        val oversized = fileTree(rootDir) {
            include("**/*.kt", "**/*.kts")
            exclude("**/build/**", ".gradle/**")
        }.files.mapNotNull { file ->
            val count = file.useLines { it.count() }
            if (count > 300) "${file.relativeTo(rootDir)}: $count" else null
        }
        check(oversized.isEmpty()) {
            "Kotlin/Gradle files over 300 lines:\n${oversized.joinToString("\n")}"
        }
    }
}
