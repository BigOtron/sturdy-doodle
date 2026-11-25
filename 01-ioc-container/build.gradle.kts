plugins {
    java
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework:spring-context:7.1.0")
}

application {
    mainClass.set("dev.shermuhammad.ioc.App")
}