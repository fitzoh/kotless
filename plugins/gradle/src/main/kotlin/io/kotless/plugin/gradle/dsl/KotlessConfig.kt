package io.kotless.plugin.gradle.dsl

import io.kotless.KotlessConfig.Optimization.MergeLambda
import org.gradle.api.Project
import java.io.File
import java.io.Serializable

/**
 * Configuration of Kotless itself
 */
@KotlessDSLTag
class KotlessConfig(project: Project) : Serializable {
    /** Name of bucket Kotless will use to store all files */
    lateinit var bucket: String

    /** Prefix with which all created resources will be prepended */
    var prefix: String = ""

    /**
     * A local directory Kotless will use to store needed binaries (like terraform)
     * By default it is `${buildDir}/kotless-bin`
     */
    var binDirectory = File(project.buildDir, "kotless-bin")

    /**
     * A local directory Kotless will use to store generated files
     * By default it is `${buildDir}/kotless-gen`
     */
    var genDirectory = File(project.buildDir, "kotless-gen")

    /**
     * Directory Kotless considers as root for File resolving
     * By default it is `projectDir`
     */
    var workDirectory = project.projectDir as File

    /** Name of configuration to use as a classpath */
    var configurationName = "compileClasspath"


    internal val terraform: Terraform = Terraform()
    /** Configuration of Terraform */
    @KotlessDSLTag
    fun terraform(configure: Terraform.() -> Unit) {
        terraform.apply(configure)
    }

    @KotlessDSLTag
    class Terraform : Serializable {
        /**
         * Version of Terraform to use.
         * By default, `0.11.14`
         */
        var version: String = "0.11.14"
        /** AWS profile from a local machine to use for Terraform operations authentication */
        lateinit var profile: String
        /** AWS region in context of which all Terraform operations should be performed */
        lateinit var region: String

        internal val backend = Backend()
        /** Configuration of Terraform backend */
        @KotlessDSLTag
        fun backend(configure: Backend.() -> Unit) {
            backend.apply(configure)
        }

        @KotlessDSLTag
        class Backend : Serializable {
            /**
             * Name of bucket, that will be used as Terraform backend storage
             * By default kotless bucket is used.
             */
            var bucket: String? = null

            /**
             * Path in a bucket to store Terraform state
             * By default it is `kotless-state/state.tfstate`
             */
            var key: String = "kotless-state/state.tfstate"

            var profile: String? = null

            var region: String? = null
        }

        internal val provider = AWSProvider()
        /** Configuration of Terraform AWS provider */
        @KotlessDSLTag
        fun provider(configure: AWSProvider.() -> Unit) {
            provider.apply(configure)
        }

        @KotlessDSLTag
        class AWSProvider : Serializable {
            /** Version of AWS provider to use */
            var version = "1.60.0"

            var profile: String? = null

            var region: String? = null
        }
    }

    internal val optimization: Optimization = Optimization()
    /** Optimizations applied during generation */
    @KotlessDSLTag
    fun optimization(configure: Optimization.() -> Unit) {
        optimization.apply(configure)
    }

    @KotlessDSLTag
    class Optimization : Serializable {
        /**
         * Optimization defines, if different lambdas should be merged into one and when.
         *
         * Basically, lambda serving few endpoints is more likely to be warm.
         *
         * There are 3 levels of merge optimization:
         * * None -- lambdas will never be merged
         * * PerPermissions -- lambdas will be merged, if they have equal permissions
         * * All -- all lambdas in context are merged in one
         */
        var mergeLambda: MergeLambda = MergeLambda.All

        /**
         * Optimization defines, if lambdas should be autowarmed and with what schedule
         *
         * Lambdas cannot be autowarmed with interval more than hour, since it has no practical sense
         */
        @KotlessDSLTag
        data class Autowarm(val enable: Boolean, val minutes: Int) : Serializable

        var autowarm: Autowarm = Autowarm(true, 5)
    }
}
