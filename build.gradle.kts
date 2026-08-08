import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    base
}

val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
    ?: error("ANDROID_SDK_ROOT or ANDROID_HOME must be set.")
val buildToolsVersion = "37.0.0"
val platformVersion = "android-35"

val appBuildDir = file("app/build")
val manifestFile = file("app/src/main/AndroidManifest.xml")
val resDir = file("app/src/main/res")
val mainJavaDir = file("app/src/main/java")

val buildToolsDir = file("$sdkRoot/build-tools/$buildToolsVersion")
val platformAndroidJar = file("$sdkRoot/platforms/$platformVersion/android.jar")
val aapt2 = file("$buildToolsDir/aapt2")
val aapt = file("$buildToolsDir/aapt")
val d8 = file("$buildToolsDir/d8")
val zipalign = file("$buildToolsDir/zipalign")
val apksigner = file("$buildToolsDir/apksigner")

val compiledResZip = file("app/build/intermediates/res/debug/compiled-res.zip")
val linkedApk = file("app/build/intermediates/apk/debug/resources-debug.apk")
val generatedRDir = file("app/build/generated/source/r/debug")
val classesDir = file("app/build/intermediates/classes/debug")
val dexDir = file("app/build/intermediates/dex/debug")
val unsignedApk = file("app/build/outputs/apk/debug/app-debug-unsigned.apk")
val alignedApk = file("app/build/outputs/apk/debug/app-debug-aligned.apk")
val signedApk = file("app/build/outputs/apk/debug/app-debug.apk")
val debugKeystore = file("app/build/signing/debug.keystore")
val logicTestDir = file("app/build/logic-tests")

fun ensureSdkTool(path: File) {
    require(path.exists()) { "Required Android SDK tool not found: ${path.absolutePath}" }
}

fun runCommand(vararg command: String): String {
    val process = ProcessBuilder(*command)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("Command failed (${command.joinToString(" ")}):\n$output")
    }
    return output.trim()
}

tasks.named("clean") {
    doLast {
        delete(appBuildDir)
    }
}

tasks.register("compileDebugResources") {
    outputs.file(compiledResZip)
    doLast {
        ensureSdkTool(aapt2)
        compiledResZip.parentFile.mkdirs()
        runCommand(
            aapt2.absolutePath,
            "compile",
            "--dir",
            resDir.absolutePath,
            "-o",
            compiledResZip.absolutePath,
        )
    }
}

tasks.register("linkDebugResources") {
    dependsOn("compileDebugResources")
    outputs.file(linkedApk)
    outputs.dir(generatedRDir)
    doLast {
        ensureSdkTool(aapt2)
        linkedApk.parentFile.mkdirs()
        generatedRDir.mkdirs()
        runCommand(
            aapt2.absolutePath,
            "link",
            "--auto-add-overlay",
            "-I",
            platformAndroidJar.absolutePath,
            "--manifest",
            manifestFile.absolutePath,
            "--java",
            generatedRDir.absolutePath,
            "-o",
            linkedApk.absolutePath,
            compiledResZip.absolutePath,
        )
    }
}

tasks.register("compileDebugJava") {
    dependsOn("linkDebugResources")
    outputs.dir(classesDir)
    doLast {
        classesDir.mkdirs()
        val sources = fileTree(mainJavaDir).matching { include("**/*.java") }.files +
            fileTree(generatedRDir).matching { include("**/*.java") }.files
        require(sources.isNotEmpty()) { "No Java sources found to compile." }
        runCommand(
            *(
                listOf(
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-classpath",
                    platformAndroidJar.absolutePath,
                    "-d",
                    classesDir.absolutePath,
                ) + sources.map { it.absolutePath }
            ).toTypedArray()
        )
    }
}

tasks.register("dexDebug") {
    dependsOn("compileDebugJava")
    outputs.dir(dexDir)
    doLast {
        ensureSdkTool(d8)
        dexDir.mkdirs()
        val classFiles = fileTree(classesDir).matching { include("**/*.class") }.files
        require(classFiles.isNotEmpty()) { "No compiled class files found for dexing." }
        runCommand(
            *(
                listOf(
                    d8.absolutePath,
                    "--lib",
                    platformAndroidJar.absolutePath,
                    "--min-api",
                    "24",
                    "--output",
                    dexDir.absolutePath,
                ) + classFiles.map { it.absolutePath }
            ).toTypedArray()
        )
    }
}

tasks.register("packageDebugUnsigned") {
    dependsOn("dexDebug")
    outputs.file(unsignedApk)
    doLast {
        ensureSdkTool(aapt)
        unsignedApk.parentFile.mkdirs()
        Files.copy(linkedApk.toPath(), unsignedApk.toPath(), StandardCopyOption.REPLACE_EXISTING)
        runCommand(
            aapt.absolutePath,
            "add",
            unsignedApk.absolutePath,
            file("${dexDir.absolutePath}/classes.dex").absolutePath,
        )
    }
}

tasks.register("generateDebugKeystore") {
    outputs.file(debugKeystore)
    doLast {
        if (!debugKeystore.exists()) {
            debugKeystore.parentFile.mkdirs()
            runCommand(
                "keytool",
                "-genkeypair",
                "-alias",
                "androiddebugkey",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "10000",
                "-storetype",
                "PKCS12",
                "-keystore",
                debugKeystore.absolutePath,
                "-storepass",
                "android",
                "-keypass",
                "android",
                "-dname",
                "CN=Android Debug,O=MINE-CHAT,C=US",
            )
        }
    }
}

tasks.register("assembleDebug") {
    dependsOn("packageDebugUnsigned", "generateDebugKeystore")
    outputs.file(signedApk)
    doLast {
        ensureSdkTool(zipalign)
        ensureSdkTool(apksigner)
        runCommand(
            zipalign.absolutePath,
            "-f",
            "4",
            unsignedApk.absolutePath,
            alignedApk.absolutePath,
        )
        runCommand(
            apksigner.absolutePath,
            "sign",
            "--ks",
            debugKeystore.absolutePath,
            "--ks-pass",
            "pass:android",
            "--key-pass",
            "pass:android",
            "--out",
            signedApk.absolutePath,
            alignedApk.absolutePath,
        )
    }
}

tasks.register("verifyLogic") {
    outputs.dir(logicTestDir)
    doLast {
        logicTestDir.mkdirs()
        val sources = listOf(
            file("app/src/main/java/com/minechat/ChatEntry.java"),
            file("app/src/main/java/com/minechat/ChatEntryFactory.java"),
            file("app/src/test/java/com/minechat/ChatEntryFactoryTest.java"),
        )
        runCommand(
            *(
                listOf(
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-d",
                    logicTestDir.absolutePath,
                ) + sources.map { it.absolutePath }
            ).toTypedArray()
        )
        runCommand(
            "java",
            "-ea",
            "-cp",
            logicTestDir.absolutePath,
            "com.minechat.ChatEntryFactoryTest",
        )
    }
}

tasks.register("validateDebugApk") {
    dependsOn("assembleDebug")
    doLast {
        println(runCommand(apksigner.absolutePath, "verify", "--print-certs", signedApk.absolutePath))
    }
}
