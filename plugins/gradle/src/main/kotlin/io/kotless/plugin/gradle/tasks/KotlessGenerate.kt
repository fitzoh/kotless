package io.kotless.plugin.gradle.tasks

import io.kotless.*
import io.kotless.Webapp
import io.kotless.parser.KotlessParser
import io.kotless.plugin.gradle.dsl.*
import io.kotless.plugin.gradle.utils.myKtSourceSet
import io.kotless.plugin.gradle.utils.myShadowJar
import io.kotless.utils.TypedStorage
import org.codehaus.plexus.util.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import java.io.File

/**
 * KotlessGenerate task generates terraform code from Kotlin code written with Kotless.
 *
 * It takes all the configuration from global KotlessDSL configuration (at `kotless` field)
 *
 * @see kotless
 *
 * Note: Task is cacheable and will regenerate code only if sources or configuration has changed.
 */
@CacheableTask
open class KotlessGenerate : DefaultTask() {
    init {
        group = "kotless"
    }

    @get:Input
    val dsl: KotlessDSL
        get() = project.kotless

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val allSources: Set<File>
        get() = project.myKtSourceSet.toSet()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val allTerraformAddition: Set<File>
        get() = project.kotless.extensions.terraform.files.additional

    @get:OutputDirectory
    val genDir: File
        get() = project.kotless.config.genDirectory

    @TaskAction
    fun generate() {
        genDir.deleteRecursively()

        val config = dsl.toSchema()

        val lambdas = TypedStorage<Lambda>()
        val statics = TypedStorage<StaticResource>()

        val webapp = dsl.webapp.let { webapp ->
            val project = webapp.project(project)
            val sources = project.myKtSourceSet
            val shadowJar = project.myShadowJar().archiveFile.get().asFile
            val dependencies = project.configurations.getByName(project.kotless.config.configurationName).files.toSet()

            val lambda = Lambda.Config(webapp.lambda.memoryMb, webapp.lambda.timeoutSec, webapp.packages)

            val result = KotlessParser.parse(sources, shadowJar, config, lambda, dependencies)

            lambdas.addAll(result.resources.dynamics)
            statics.addAll(result.resources.statics)

            val route53 = webapp.route53?.toSchema()
            Webapp(
                route53,
                Webapp.ApiGateway(project.name,
                    webapp.deployment.toSchema(),
                    result.routes.dynamics,
                    result.routes.statics
                ),
                Webapp.Events(result.events.scheduled)
            )
        }

        val schema = Schema(config, webapp, lambdas, statics)

        val generated = KotlessEngine.generate(schema)

        for (file in dsl.extensions.terraform.files.additional) {
            require(generated.all { it.name != file.name }) { "Extending terraform file with name ${file.name} clashes with generated file" }
            FileUtils.copyFile(file, File(genDir, file.name))
        }
    }
}
