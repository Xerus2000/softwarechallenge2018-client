import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.internal.TaskOutputCachingState
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.Writer
import java.util.Arrays

val client = properties["c"] as String?
val clientParams = if (client != null) listOf("-c", client) else emptyList()

version = file("src/xerus/softwarechallenge/logic2018/${client ?: "Jumper1_8"}.kt").bufferedReader().use {
	var line: String
	do {
		line = it.readLine()
	} while (!line.contains("Jumper"))
	line.split('"')[1]
}

plugins {
	kotlin("jvm") version "1.2.41"
	id("com.github.johnrengelman.shadow") version "2.0.3"
	application
}

repositories { jcenter() }

dependencies {
	compile("xerus.util", "kotlin")
	compile(fileTree("lib"))
}

java.sourceSets.getByName("main").java.srcDir("src")
java.sourceSets.getByName("main").resources.srcDir("resources")

val javaArgs = listOf("-Dfile.encoding=UTF-8"
		, "-XX:NewRatio=1"
		, "-ms1500000000", "-mx1500000000"
		, "-XX:MaxGCPauseMillis=80", "-XX:GCPauseIntervalMillis=1000"
		, "-XX:TargetSurvivorRatio=90"
)

val cms = listOf("-XX:+UseConcMarkSweepGC"
		, "-XX:-UseParNewGC"
		, "-XX:CMSInitiatingOccupancyFraction=80", "-XX:+UseCMSInitiatingOccupancyOnly"
		, "-XX:+ScavengeBeforeFullGC", "-XX:+CMSScavengeBeforeRemark")

val gcDebugParams = if(properties["nogc"] != null) emptyList() else listOf(
		"-XX:+PrintGCDetails", "-XX:+PrintGCTimeStamps"
		, "-XX:+PrintPromotionFailure", "-noverify"
)

application {
	applicationName = "Jumper 1"
	mainClassName = "xerus.softwarechallenge.StarterKt"
	applicationDefaultJvmArgs = javaArgs + cms + gcDebugParams
}

tasks {
	
	val MAIN = "_main"
	val clients = file("../clients")
	val jumper = "Jumper-$version"
	
	"scripts"(Exec::class) {
		val script = clients.resolve("start-client.sh")
		doFirst {
			script.bufferedWriter().run {
				write("""
					#!/usr/bin/env bash
					client=$(dirname "${'$'}{BASH_SOURCE[0]}")/$jumper.jar
					if [ ${'$'}# -eq 0 ]; 
					then args=0;
					else args=2;
					${"if [ -f $1 ]; then client=$1; fi"}
					fi;
					java ${(javaArgs + cms + gcDebugParams).joinToString(" ")} -jar ${'$'}client "${'$'}{@}"
				""".trimIndent())
				close()
			}
		}
		commandLine("chmod", "+x", script)
	}
	
	withType<KotlinCompile> {
		mustRunAfter("clean")
		kotlinOptions {
			jvmTarget = "1.8"
			freeCompilerArgs = listOf("-Xno-param-assertions", "-Xno-call-assertions")
		}
	}
	
	"run"(JavaExec::class) {
		group = MAIN
		args = listOf("-d", "2") + clientParams
	}
	
	"shadowJar"(ShadowJar::class) {
		baseName = "Jumper"
		classifier = ""
		destinationDir = clients
		from(java.sourceSets.getByName("main").output)
		dependsOn("classes")
	}
	
	"processResources" {
		setOnlyIf { true }
		doFirst {
			sync {
				from("src/xerus/softwarechallenge")
				into("resources/sources")
			}
			file("resources/activeclient").writeText(client.orEmpty())
		}
	}
	
	"zip"(Zip::class) {
		dependsOn("jar")
		from(clients)
		include("$jumper.jar", "start-client.sh")
		archiveName = "$jumper.zip"
		destinationDir = file("..")
		doFirst {
			file("..").listFiles { _, name -> name.matches(Regex("Jumper.*.zip")) }.forEach { it.delete() }
		}
	}
	
	tasks.replace("jar").apply {
		group = MAIN
		dependsOn("shadowJar", "scripts")
		doFirst {
			//file("..").listFiles { _, name -> name.startsWith("Jumper") && name.endsWith("jar") && name != "$jumper.jar" }.forEach { it.renameTo(file("../Archiv/Jumper/${it.name}").also { it.delete() }) }
		}
	}
	
}

println("Java version: ${JavaVersion.current()}")
println("Version: $version")
