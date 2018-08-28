plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.10.0"
}

group = "org.included.multiproject"
version = "1.0"

gradlePlugin {
    plugins {
        create("included-plugin") {
            id = "included-plugin"
            implementationClass = "org.included.IncludedBuildClass"
        }
    }
}

repositories {
    jcenter()
}

//val a = GreetPlugin::class
//val a1 = GreetPlugin::class
//val b = MeetPlugin::class

