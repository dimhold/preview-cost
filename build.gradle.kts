plugins {
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("com.h2database:h2:2.3.232")
    implementation("org.postgresql:postgresql:42.7.4")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

application {
    mainClass = "dev.dimhold.previewcost.Bench"
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed") }
}
