apply(from="../../commonHeader.gradle")
apply(from="../../commonVersions.gradle", to=project.ext)

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.0"
    `java-gradle-plugin`
}

dependencies {
    compileOnly(gradleApi())
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${rootProject.ext["kotlinVersion"]}")
    compileOnly("com.android.tools.build:gradle:3.5.0")
    runtimeOnly("com.android.tools.build:gradle:${project.ext["buildVersion"]}")
}

gradlePlugin {
    plugins {
        create("examplePlugin") {
            id = "com.example.apiuser.example-plugin"
            implementationClass = "com.example.apiuser.ExamplePlugin"
        }
    }
}
