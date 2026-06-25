plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("${rootProject.projectDir}/detekt.yml"))
    }
    configurations.configureEach {
        resolutionStrategy {
            force(
                "androidx.lifecycle:lifecycle-runtime:2.8.7",
                "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7",
                "androidx.lifecycle:lifecycle-common:2.8.7",
                "androidx.lifecycle:lifecycle-process:2.8.7",
                "androidx.lifecycle:lifecycle-common-java8:2.8.7",
            )
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}