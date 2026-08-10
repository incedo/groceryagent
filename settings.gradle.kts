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
include(":apps:importer")
include(":core:catalog")
include(":core:events")
include(":integration:picnic-client")
include(":integration:object-storage")
include(":integration:postgres")
