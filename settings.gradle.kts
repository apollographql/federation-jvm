rootProject.name = "graphql-java-federation"

pluginManagement {
    plugins {
        fun String.getVersion() = extra["$this.version"].toString()
        fun PluginDependenciesSpec.resolve(id: String, versionKey: String = id) = id(id) version versionKey.getVersion()

        resolve("com.google.protobuf")
        resolve("com.jfrog.bintray")
    }
}
