import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // core는 bootJar 대상이 아니지만, 버전은 똑같이 Boot BOM을 따라간다.
    // api로 선언해서 core를 의존하는 api/batch에도 이 버전 제약이 전파되게 한다.
    api(platform(SpringBootPlugin.BOM_COORDINATES))
}
