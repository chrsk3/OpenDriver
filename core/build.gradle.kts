plugins {
    kotlin("jvm")
    application
}

group = "org.opendriver"
version = "1.0.0"

application {
    mainClass.set("org.opendriver.core.cli.OpenDriverCliKt")
}


dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
