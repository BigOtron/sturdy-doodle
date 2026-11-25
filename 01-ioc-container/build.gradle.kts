plugins {
    id("java-conventions")
    application
}

dependencies {
    implementation("org.springframework:spring-context:7.1.0")
}

application {
    mainClass.set("dev.shermuhammad.ioc.App")
}