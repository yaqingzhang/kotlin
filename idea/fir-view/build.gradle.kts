/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
//    val compile by configurations
//    val compileOnly by configurations


    compile(project(":kotlin-stdlib-jre8"))
    compileOnly(project(":kotlin-reflect-api"))
    compile(project(":core:descriptors"))
    compile(project(":compiler:fir:tree"))
    compile(project(":compiler:fir:psi2fir"))

    compile(project(":idea:idea-core"))

    compileOnly(intellijCoreDep()) { includeJars("intellij-core", "annotations") }
    compileOnly(intellijDep()) { includeJars("util", "openapi", "idea", "asm-all", "jdom", "trove4j", "guava", rootProject = rootProject) }
    compileOnly(intellijPluginDep("gradle")) { includeJars("gradle-tooling-api", "gradle", rootProject = rootProject) }
}

sourceSets {
    "main" {
        projectDefault()
    }
    "test" {}
}
