plugins {
    id("com.android.library")
    id("com.example.apiuser.example-plugin")
}

android {
    compileSdk = property("latestCompileSdk") as Int
    flavorDimensions += "color"
    productFlavors {
        create("yellow") {
        }
    }
}
