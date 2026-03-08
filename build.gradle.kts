import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.antlr)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.detekt)
}

group = "com.github.metametamoon"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

sourceSets["main"].kotlin.srcDir("build/generatedAntlr/")

dependencies {
  implementation("com.strumenta:antlr-kotlin-runtime-jvm:1.0.0")
  implementation("com.michael-bull.kotlin-result:kotlin-result:2.1.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(23)
  compilerOptions {
    freeCompilerArgs.addAll(
      "-Xcontext-parameters",
      "-Xreturn-value-checker=full",
      "-Werror",
      "-Xwarning-level=ERROR_SUPPRESSION:disabled", // for generated code
    )
  }
}

tasks.test { useJUnitPlatform() }

val generateKotlinGrammarSource =
  tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")
    source = fileTree(layout.projectDirectory.dir("antlr")) { include("**/*.g4") }
    val pkgName = "generated.antlr"
    packageName = pkgName
    arguments = listOf("-visitor")
    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile

    // workaround to allow for stricter warnings
    doLast {
      val generatedFolder: File = (this as AntlrKotlinTask).outputDirectory!!
      val generatedFiles: FileTree = project.fileTree(generatedFolder) { include("**/*.kt") }
      generatedFiles.forEach { file: File ->
        val content = file.readText()
        val suppression = "@file:Suppress(\"all\", \"warnings\", \"unchecked\", \"unused\")"
        file.writeText("$suppression\n$content")
      }
    }
  }

tasks.withType<KotlinCompile>().configureEach { dependsOn(generateKotlinGrammarSource) }

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
    dependsOn(tasks.ktfmtCheckMain)
    dependsOn(tasks.ktfmtCheckTest)
    dependsOn(tasks.detektMain)
    dependsOn(tasks.detektTest)
  }

tasks.withType<Test>().configureEach { useJUnitPlatform() }

tasks.named("generateKotlinGrammarSource") {}
