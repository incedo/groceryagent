pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "grocery-automate"

include(":apps:backend")
include(":core:catalog")
include(":core:events")
include(":integration:picnic-client")
include(":integration:postgres")
