plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.battery"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)

    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(libs.datastore)
    implementation(libs.androidx.work)
}
