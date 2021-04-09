plugins {
    id("com.android.library")
    id("com.example.apiuser.example-plugin")
}

android {
    compileSdkVersion(30)
    flavorDimensions += "color"
    productFlavors {
        create("yellow") {
        }
    }
}
