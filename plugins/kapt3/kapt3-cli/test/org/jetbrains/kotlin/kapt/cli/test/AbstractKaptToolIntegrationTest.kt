/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt.cli.test

import com.intellij.openapi.util.SystemInfo
import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.arguments.readArgumentsFromArgFile
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@RunWith(JUnit3RunnerWithInners::class)
abstract class AbstractKaptToolIntegrationTest : TestCase() {
    fun doTest(filePath: String) {
        val testDir = File(filePath)
        val testFile = File(testDir, "build.txt")
        assert(testFile.isFile) { "build.txt doesn't exist" }

        val tempDir = Files.createTempDirectory("kapt-cli").toFile()
        try {
            testDir.listFiles().forEach { it.copyRecursively(File(tempDir, it.name)) }
            doTestInTempDirectory(testFile, File(tempDir, testFile.name))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private class GotResult(val actual: String): RuntimeException()

    private fun doTestInTempDirectory(originalTestFile: File, testFile: File) {
        val tempDir = testFile.parentFile
        val sections = Section.parse(testFile)

        for (section in sections) {
            try {
                when (section.name) {
                    "mkdir" -> section.args.forEach { File(tempDir, it).mkdirs() }
                    "copy" -> copyFile(originalTestFile.parentFile, tempDir, section.args)
                    "kotlinc" -> runKotlinDistBinary(tempDir, "kotlinc", section.args)
                    "kapt" -> runKotlinDistBinary(tempDir, "kapt", section.args)
                    "javac" -> runJavac(tempDir, section.args)
                    "java" -> runJava(tempDir, section.args)
                    "after" -> {}
                    else -> error("Unknown section name ${section.name}")
                }
            } catch (e: GotResult) {
                val actual = sections.replacingSection("after", e.actual).render()
                KotlinTestUtils.assertEqualsToFile(originalTestFile, actual)
                return
            } catch (e: Throwable) {
                throw RuntimeException("Section ${section.name} failed:\n${section.content}", e)
            }
        }
    }

    private fun copyFile(testDir: File, tempDir: File, args: List<String>) {
        assert(args.size == 2)
        val source = File(testDir, args[0])
        val target = File(tempDir, args[1]).also { it.parentFile.mkdirs() }
        source.copyRecursively(target)
    }

    private fun runKotlinDistBinary(tempDir: File, name: String, args: List<String>) {
        val executableName = if (SystemInfo.isWindows) name + ".bat" else name
        val executablePath = File("dist/kotlinc/bin/" + executableName).absolutePath
        runProcess(tempDir, executablePath, args)
    }

    private fun runJavac(tempDir: File, args: List<String>) {
        val executableName = if (SystemInfo.isWindows) "javac.exe" else "javac"
        val executablePath = File(getJdk8Home(), "bin/" + executableName).absolutePath
        runProcess(tempDir, executablePath, args)
    }

    private fun runJava(tempDir: File, args: List<String>) {
        val outputFile = File(tempDir, "javaOutput.txt")

        val executableName = if (SystemInfo.isWindows) "java.exe" else "java"
        val executablePath = File(getJdk8Home(), "bin/" + executableName).absolutePath
        runProcess(tempDir, executablePath, args) {
            redirectOutput(ProcessBuilder.Redirect.appendTo(outputFile))
            redirectError(ProcessBuilder.Redirect.appendTo(outputFile))
        }

        throw GotResult(outputFile.takeIf { it.isFile }?.readText() ?: "")
    }

    private fun runProcess(tempDir: File, executablePath: String, args: List<String>, setup: ProcessBuilder.() -> Unit = {}) {
        fun err(message: String): Nothing = error("$message: $name (${args.joinToString(" ")})")

        val errorFile = File(tempDir, "processError.txt")

        val transformedArgs = transformArguments(args).toTypedArray()
        val process = ProcessBuilder(executablePath, *transformedArgs).directory(tempDir)
            .inheritIO().redirectError(ProcessBuilder.Redirect.to(errorFile))
            .apply(setup).start()

        if (!process.waitFor(2, TimeUnit.MINUTES)) err("Process is still alive")
        if (process.exitValue() != 0) {
            throw GotResult(buildString {
                append("Return code: ").appendln(process.exitValue()).appendln()
                appendln(errorFile.readText())
            })
        }
    }

    private fun transformArguments(args: List<String>): List<String> {
        return args.map { it.replace("%KOTLIN_STDLIB%", File("dist/kotlinc/lib/kotlin-stdlib.jar").absolutePath) }
    }

    private fun getJdk8Home(): File {
        val homePath = System.getenv()["JDK_18"] ?: error("Can't find JDK 1.8 home")
        return File(homePath)
    }
}

private val Section.args get() = readArgumentsFromArgFile(content)