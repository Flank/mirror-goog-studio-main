apply(from="../../commonHeader.gradle")
apply(from="../../commonVersions.gradle", to=project.ext)

plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("mockKspPlugin") {
            id = "com.google.devtools.ksp"
            implementationClass = "MockKspPlugin"
        }
    }
}
