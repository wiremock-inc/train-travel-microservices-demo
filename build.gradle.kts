plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.wiremock"
version = "0.1.0"

// Align the Spring Boot BOM's managed Kotlin version with the Kotlin plugin
extra["kotlin.version"] = "2.4.20"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

val jaxb = configurations.create("jaxb") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-web-services")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.glassfish.jaxb:jaxb-runtime")
    implementation("com.sun.xml.messaging.saaj:saaj-impl")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    jaxb("com.sun.xml.bind:jaxb-xjc:4.0.5")
}

// --- SOAP client codegen: JAXB classes generated from the PayRail contract ---

val generatedWsdlDir = layout.buildDirectory.dir("generated/wsdl")

val generateSoapClient = tasks.register("generateSoapClient", JavaExec::class) {
    group = "build"
    description = "Generates JAXB classes from contracts/payrail.xsd (the schema behind contracts/payrail.wsdl)"
    val schema = layout.projectDirectory.file("contracts/payrail.xsd")
    inputs.file(schema)
    outputs.dir(generatedWsdlDir)
    classpath = jaxb
    mainClass.set("com.sun.tools.xjc.XJCFacade")
    args(
        "-p", "com.wiremock.traintravel.payrail.generated",
        "-d", generatedWsdlDir.get().asFile.absolutePath,
        "-quiet",
        schema.asFile.absolutePath,
    )
}

sourceSets.main {
    java.srcDir(generatedWsdlDir)
}

tasks.compileKotlin {
    dependsOn(generateSoapClient)
}

tasks.withType<Test> {
    useJUnitPlatform()
}