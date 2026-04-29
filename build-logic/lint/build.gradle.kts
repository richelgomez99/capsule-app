plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.10.1")
    compileOnly("com.android.tools.lint:lint-checks:31.10.1")

    testImplementation("com.android.tools.lint:lint-tests:31.10.1")
    testImplementation("com.android.tools.lint:lint:31.10.1")
    testImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
