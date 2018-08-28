rootProject.name = "multiproject"

includeBuild("gradle-plugin") {
    dependencySubstitution {
        substitute(module("multiproject:gradle-plugin")).with(project(":"))
    }
}

// Work around for https://github.com/gradle/gradle-native/issues/522
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if(requested.id.id == "included-plugin") {
                useModule("multiproject:gradle-plugin:latest-integration")
            }
        }
    }
}


/*includeBuild("gradle-plugin") {
    dependencySubstitution {
        substitute(module("multiproject:gradle-plugin")).with(project(":"))
    }
}

// Work around for https://github.com/gradle/gradle-native/issues/522
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if(requested.id.id == "included-plugin") {
                useModule("multiproject:gradle-plugin:latest-integration")
            }
        }
    }
}*/
