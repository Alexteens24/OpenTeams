plugins {
    id("com.gradleup.shadow")
}

dependencies {
    compileOnly(project(":openteams-api"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.flywaydb:flyway-core:11.20.3")
    implementation("org.flywaydb:flyway-mysql:11.20.3")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation(project(":openteams-api"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testImplementation("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:mariadb")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.zaxxer.hikari", "me.alexisbinh.openteams.homes.internal.hikari")
    relocate("org.flywaydb", "me.alexisbinh.openteams.homes.internal.flyway")
    relocate("com.github.benmanes.caffeine", "me.alexisbinh.openteams.homes.internal.caffeine")
}

tasks.jar { enabled = false }
tasks.assemble { dependsOn(tasks.shadowJar) }
