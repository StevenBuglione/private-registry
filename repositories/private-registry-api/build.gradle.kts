import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.w3c.dom.Element
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Properties
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    java
    checkstyle
    jacoco
    pmd
    id("org.springframework.boot") version "4.1.0"
    id("com.diffplug.spotless") version "8.8.0"
    id("net.ltgt.errorprone") version "5.1.0"
    id("com.github.spotbugs") version "6.5.9"
    id("info.solidsoft.pitest") version "1.19.0"
    id("org.cyclonedx.bom") version "3.3.0"
}

group = "com.stevenbuglione.registry"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.0"))

    constraints {
        implementation("org.apache.tomcat.embed:tomcat-embed-core:11.0.24") {
            because("11.0.24 contains the July 2026 Tomcat security fixes")
        }
        implementation("org.apache.tomcat.embed:tomcat-embed-el:11.0.24") {
            because("keep the embedded Tomcat components on one patched release")
        }
        implementation("org.apache.tomcat.embed:tomcat-embed-websocket:11.0.24") {
            because("keep the embedded Tomcat components on one patched release")
        }
        implementation("com.fasterxml.jackson.core:jackson-core:2.21.5") {
            because("keep Jackson 2 components aligned with the patched databind release")
        }
        implementation("com.fasterxml.jackson.core:jackson-databind:2.21.5") {
            because("2.21.5 fixes CVE-2026-54515")
        }
        implementation("org.apache.logging.log4j:log4j-api:2.25.5") {
            because("2.25.5 fixes CVE-2026-49844")
        }
        implementation("org.apache.logging.log4j:log4j-to-slf4j:2.25.5") {
            because("keep the Log4j bridge aligned with the patched API release")
        }
    }

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql:42.7.12")
    implementation("org.jfrog.artifactory.client:artifactory-java-client-services:2.21.2")
    compileOnly("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("com.github.ben-manes.caffeine:caffeine")

    compileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("org.checkerframework:checker-qual:4.2.1")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.13.8")
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")

    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.0"))
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testCompileOnly("org.jspecify:jspecify:1.0.0")
    testCompileOnly("org.checkerframework:checker-qual:4.2.1")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    buildInfo {
        properties {
            excludes.set(setOf("time"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        excludedPaths.set(".*/build/generated/.*")
        check("NullAway", CheckSeverity.ERROR)
        // JSpecify packages are opted in explicitly and protected by verifyNullMarkedPackages below.
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    enabled = false
}

spotless {
    java {
        target("src/*/java/**/*.java")
        googleJavaFormat("1.35.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "13.8.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxErrors = 0
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

pmd {
    toolVersion = "7.26.0"
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = emptyList()
    rulesMinimumPriority.set(3)
    isConsoleOutput = true
    // Existing findings are ratcheted precisely by pmdBaselineCheck; PMD itself must finish to emit XML.
    isIgnoreFailures = true
}

tasks.withType<Pmd>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

fun pmdFindingKeys(report: File, sourceSet: String, projectDirectory: File): List<String> {
    if (!report.isFile) return emptyList()
    val document =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(report)
    val findings = mutableListOf<String>()
    val files = document.getElementsByTagNameNS("*", "file")
    for (fileIndex in 0 until files.length) {
        val fileElement = files.item(fileIndex) as Element
        val sourceFile = File(fileElement.getAttribute("name"))
        val relativePath =
            sourceFile.relativeTo(projectDirectory).invariantSeparatorsPath
        val violations = fileElement.getElementsByTagNameNS("*", "violation")
        for (violationIndex in 0 until violations.length) {
            val violation = violations.item(violationIndex) as Element
            findings +=
                listOf(
                        sourceSet,
                        relativePath,
                        violation.getAttribute("rule"),
                        violation.getAttribute("class"),
                        violation.getAttribute("method"),
                        violation.getAttribute("variable"),
                    )
                    .joinToString("|")
        }
    }
    return findings.sorted()
}

val pmdBaseline = layout.projectDirectory.file("config/pmd/baseline.txt")
val pmdMainReport = layout.buildDirectory.file("reports/pmd/main.xml")
val pmdTestReport = layout.buildDirectory.file("reports/pmd/test.xml")

val pmdBaselineCheck = tasks.register("pmdBaselineCheck") {
    group = "verification"
    description = "Fails when PMD reports a finding absent from the reviewed legacy baseline."
    dependsOn(tasks.named("pmdMain"), tasks.named("pmdTest"))
    inputs.file(pmdBaseline)
    inputs.files(pmdMainReport, pmdTestReport)
    doLast {
        val expected =
            pmdBaseline.asFile
                .readLines()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toMutableList()
        val current =
            pmdFindingKeys(pmdMainReport.get().asFile, "main", projectDir) +
                pmdFindingKeys(pmdTestReport.get().asFile, "test", projectDir)
        val unexpected = current.filterNot(expected::remove)
        check(unexpected.isEmpty()) {
            "New PMD findings are not in config/pmd/baseline.txt:\n" +
                unexpected.joinToString("\n") { "  $it" }
        }
    }
}

tasks.register("updatePmdBaseline") {
    group = "verification"
    description = "Rewrites the reviewed PMD baseline; use only when accepting debt intentionally."
    dependsOn(tasks.named("pmdMain"), tasks.named("pmdTest"))
    doLast {
        val findings =
            pmdFindingKeys(pmdMainReport.get().asFile, "main", projectDir) +
                pmdFindingKeys(pmdTestReport.get().asFile, "test", projectDir)
        pmdBaseline.asFile.parentFile.mkdirs()
        pmdBaseline.asFile.writeText(
            "# Reviewed legacy PMD findings. New keys fail pmdBaselineCheck.\n" +
                findings.sorted().joinToString("\n", postfix = "\n")
        )
    }
}

spotbugs {
    toolVersion.set("4.10.3")
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.HIGH)
    excludeFilter.set(file("config/spotbugs/exclude-filter.xml"))
    ignoreFailures.set(false)
    showProgress.set(true)
}

tasks.withType<SpotBugsTask>().configureEach {
    val taskReportName = name
    reports.create("xml") {
        required.set(true)
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/$taskReportName.xml"))
    }
    reports.create("html") {
        required.set(true)
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/$taskReportName.html"))
    }
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

val jacocoCoverageBaseline = layout.projectDirectory.file("config/jacoco/baseline.properties")
val jacocoBaselineProperties =
    Properties().apply {
        jacocoCoverageBaseline.asFile.inputStream().use(::load)
    }

fun jacocoBaselineCount(name: String): Long =
    jacocoBaselineProperties.getProperty(name)?.toLongOrNull()
        ?: error("Missing or invalid $name in ${jacocoCoverageBaseline.asFile}")

val baselineLineCovered = jacocoBaselineCount("line.covered")
val baselineLineMissed = jacocoBaselineCount("line.missed")
val baselineBranchCovered = jacocoBaselineCount("branch.covered")
val baselineBranchMissed = jacocoBaselineCount("branch.missed")

fun coveredRatio(covered: Long, missed: Long): BigDecimal =
    BigDecimal.valueOf(covered)
        .divide(BigDecimal.valueOf(covered + missed), 12, RoundingMode.DOWN)

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    inputs.file(jacocoCoverageBaseline)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = coveredRatio(baselineLineCovered, baselineLineMissed)
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = coveredRatio(baselineBranchCovered, baselineBranchMissed)
            }
        }
    }
}

val jacocoBaselineCheck = tasks.register("jacocoBaselineCheck") {
    group = "verification"
    description = "Fails when aggregate line or branch coverage regresses from the reviewed legacy baseline."
    dependsOn(tasks.named("jacocoTestReport"))
    val report = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")
    inputs.file(jacocoCoverageBaseline)
    inputs.file(report)
    doLast {
        val documentFactory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
        val document = documentFactory.newDocumentBuilder().parse(report.get().asFile)
        fun counter(type: String): Pair<Long, Long> {
            val element =
                (0 until document.documentElement.childNodes.length)
                    .map(document.documentElement.childNodes::item)
                    .filterIsInstance<Element>()
                    .single { it.tagName == "counter" && it.getAttribute("type") == type }
            return element.getAttribute("covered").toLong() to element.getAttribute("missed").toLong()
        }
        fun checkRatio(type: String, current: Pair<Long, Long>, baseline: Pair<Long, Long>) {
            val (currentCovered, currentMissed) = current
            val (baselineCovered, baselineMissed) = baseline
            val currentTotal = currentCovered + currentMissed
            val baselineTotal = baselineCovered + baselineMissed
            check(currentCovered * baselineTotal >= baselineCovered * currentTotal) {
                "$type coverage regressed: $currentCovered/$currentTotal is below the reviewed " +
                    "baseline $baselineCovered/$baselineTotal. Improve tests or explicitly review " +
                    "config/jacoco/baseline.properties; do not lower it silently."
            }
        }
        checkRatio("LINE", counter("LINE"), baselineLineCovered to baselineLineMissed)
        checkRatio("BRANCH", counter("BRANCH"), baselineBranchCovered to baselineBranchMissed)
    }
}

pitest {
    pitestVersion.set("1.25.8")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(setOf("com.stevenbuglione.registry.*"))
    targetTests.set(setOf("com.stevenbuglione.registry.*"))
    // Every mutation worker is a JVM; keep local and CI memory use predictable.
    threads.set((Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4))
    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)
    // Reviewed whole-codebase baseline (2026-07-22): 41% mutation score and 39% line
    // coverage for mutated classes. Raise these ratchets as tests improve; never lower them silently.
    mutationThreshold.set(41)
    coverageThreshold.set(38)
    timeoutConstInMillis.set(4_000)
}

val nullMarkedProductionPackages =
    listOf(
        "artifactory",
        "catalog",
        "config",
        "eventing",
        "eventing.webhook",
        "health",
        "ingestion",
        "model",
        "security",
        "security.identity",
        "seed",
        "web",
    )

val verifyNullMarkedPackages = tasks.register("verifyNullMarkedPackages") {
    group = "verification"
    description = "Prevents regression of the production packages enforced by NullAway/JSpecify."
    val descriptors =
        nullMarkedProductionPackages.map { packageName ->
            layout.projectDirectory.file(
                "src/main/java/com/stevenbuglione/registry/${packageName.replace('.', '/')}/package-info.java"
            )
        }
    inputs.files(descriptors)
    doLast {
        val missing =
            descriptors.filter { descriptor ->
                !descriptor.asFile.isFile || !descriptor.asFile.readText().contains("@NullMarked")
            }
        check(missing.isEmpty()) {
            "NullAway coverage regressed; missing @NullMarked from: " +
                missing.joinToString { it.asFile.relativeTo(projectDir).invariantSeparatorsPath }
        }
    }
}

tasks.named("check") {
    dependsOn(
        tasks.named("spotlessCheck"),
        tasks.named("jacocoTestCoverageVerification"),
        jacocoBaselineCheck,
        verifyNullMarkedPackages,
        pmdBaselineCheck,
    )
}

tasks.register("qualityLocal") {
    group = "verification"
    description = "Runs the fast, mandatory developer quality gate."
    dependsOn(tasks.named("spotlessCheck"), tasks.named("test"), verifyNullMarkedPackages)
}

tasks.register("qualityPr") {
    group = "verification"
    description = "Runs pull-request quality, coverage, security, and SBOM gates."
    dependsOn(tasks.named("check"), tasks.named("cyclonedxDirectBom"))
}

tasks.register("qualityNightly") {
    group = "verification"
    description = "Runs the full pull-request gate plus mutation testing."
    dependsOn(tasks.named("qualityPr"), tasks.named("pitest"))
}
