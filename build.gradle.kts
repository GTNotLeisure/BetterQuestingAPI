plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}
