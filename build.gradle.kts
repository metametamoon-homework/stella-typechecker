import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
  kotlin("jvm") version "2.3.0"
  id("com.strumenta.antlr-kotlin") version "1.0.0"
  id("com.ncorti.ktfmt.gradle") version "0.25.0"
  id("dev.detekt") version "2.0.0-alpha.2"
}

group = "com.github.metametamoon"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

sourceSets["main"].kotlin.srcDir("build/generatedAntlr/com/strumenta/antlrkotlin/parsers/generated")

dependencies {
  implementation("com.strumenta:antlr-kotlin-runtime-jvm:1.0.0")
  implementation("com.michael-bull.kotlin-result:kotlin-result:2.1.0")
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(23)
  compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

tasks.test { useJUnitPlatform() }

val generateKotlinGrammarSource =
  tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    // ANTLR .g4 files are under {example-project}/antlr
    // Only include *.g4 files. This allows tools (e.g., IDE plugins)
    // to generate temporary files inside the base path
    source = fileTree(layout.projectDirectory.dir("antlr")) { include("**/*.g4") }

    // We want the generated source files to have this package name
    val pkgName = "com.strumenta.antlrkotlin.parsers.generated"
    packageName = pkgName

    // We want visitors alongside listeners.
    // The Kotlin target language is implicit, as is the file encoding (UTF-8)
    arguments = listOf("-visitor")

    // Generated files are outputted inside build/generatedAntlr/{package-name}
    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
  }

ktfmt { googleStyle() }

detekt {
  toolVersion = "2.0.0-alpha.2"
  config.setFrom(file("detekt.yml"))
  buildUponDefaultConfig = true
}

tasks.detektMain {
  exclude("**/StellaLexer**")
  exclude("**/StellaParser**")
}

val runCodeQualityChecks: TaskProvider<Task> =
  tasks.register("codeQuality") {
    group = "verification"
    dependsOn(tasks.ktfmtFormatMain)
    dependsOn(tasks.detektMain)
  }
