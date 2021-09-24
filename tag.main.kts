#!/usr/bin/env kotlin
import java.io.File
import kotlin.system.exitProcess

/**
 * A script to run locally in order to make a release.
 *
 * You need kotlin installed on your machine
 */

val arguments = args.toMutableList()

val force = arguments.remove("-f")

if (!force) {
  if (runCommand("git", "status", "--porcelain").isNotEmpty()) {
    println("Your git repo is not clean. Make sur to stash or commit your changes before making a release")
    exitProcess(1)
  }
}

check(getCurrentVersion().endsWith("-SNAPSHOT")) {
  "Version '${getCurrentVersion()} is not a -SNAPSHOT, check your working directory"
}

check(arguments.size == 1) {
  "tag.main.kts [version to tag]"
}
val tagVersion = arguments[0]
val nextSnapshot = getNextSnapshot(tagVersion)


while (true) {
  println("Current version is '${getCurrentVersion()}'.")
  println("Tag '$tagVersion' and bump to $nextSnapshot [y/n]?")

  when (readLine()!!.trim()) {
    "y" -> break
    "n" -> {
      println("Aborting.")
      exitProcess(1)
    }
  }
}

setCurrentVersion(tagVersion)
setReadmeVersion(tagVersion)
runCommand("git", "commit", "-a", "-m", "release $tagVersion")
runCommand("git", "tag", "v$tagVersion")

setCurrentVersion(nextSnapshot)
runCommand("git", "commit", "-a", "-m", "version is now $nextSnapshot")

println("Everything is done. Verify everything is ok and type `git push origin main` to trigger the new version.")

fun runCommand(vararg args: String): String {
  val builder = ProcessBuilder(*args)
    .redirectError(ProcessBuilder.Redirect.INHERIT)

  val process = builder.start()
  val ret = process.waitFor()

  val output = process.inputStream.bufferedReader().readText()
  if (ret != 0) {
    throw java.lang.Exception("command ${args.joinToString(" ")} failed:\n$output")
  }

  return output
}

fun File.replace(pattern: String, replacement: String) {
  writeText(readText().replace(Regex(pattern, RegexOption.DOT_MATCHES_ALL), replacement))
}

fun setReadmeVersion(version: String) {
  File("README.md").replace(
    pattern = "implementation 'com.apollographql.federation:federation-graphql-java-support:[^']*'",
    replacement = "implementation 'com.apollographql.federation:federation-graphql-java-support:$version'",
  )
}

fun setCurrentVersion(version: String) {
  File("graphql-java-support/pom.xml")
    .replace(
      pattern = "<artifactId>federation-parent</artifactId>\n        <version>[^<]*</version>",
      replacement = "<artifactId>federation-parent</artifactId>\n        <version>$version</version>",
    )
  File("graphql-java-support-api/pom.xml")
    .replace(
      pattern = "<artifactId>federation-parent</artifactId>\n        <version>[^<]*</version>",
      replacement = "<artifactId>federation-parent</artifactId>\n        <version>$version</version>",
    )
  File("spring-example/pom.xml")
    .replace(
      pattern = "<artifactId>federation-parent</artifactId>\n        <version>[^<]*</version>",
      replacement = "<artifactId>federation-parent</artifactId>\n        <version>$version</version>",
    )
  File("pom.xml")
    .replace(
      pattern = "<artifactId>federation-parent</artifactId>\n    <version>[^<]*</version>",
      replacement = "<artifactId>federation-parent</artifactId>\n    <version>$version</version>",
    )
}

fun getCurrentVersion(): String {
  return Regex(".*<artifactId>federation-parent</artifactId>.    <version>([^<]*)</version>.*", RegexOption.DOT_MATCHES_ALL)
    .matchEntire(File("pom.xml").readText())
    ?.groupValues
    ?.get(1) ?: error("Cannot find version")
}


fun getNextSnapshot(version: String): String {
  check(!version.contains("SNAPSHOT")) {
    "version should not contain 'SNAPSHOT': $version"
  }
  val components = version.split(".").toMutableList()
  val part = components.removeLast().toIntOrNull() ?: error("Cannot find a number to bump in $version")
  components.add("${part + 1}")
  return components.joinToString(".") + "-SNAPSHOT"
}