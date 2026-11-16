import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
	java
	id("org.springframework.boot")
}

group = "com.hy"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(platform(SpringBootPlugin.BOM_COORDINATES))

	implementation(project(":core"))

	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("tools.jackson.dataformat:jackson-dataformat-xml")
	implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")

	// Lombok: 컴파일 시점에만 생성자 등을 만들어주는 애노테이션 프로세서.
	// core의 compileOnly는 batch로 전파되지 않으므로 여기에도 따로 선언.
	compileOnly("org.projectlombok:lombok")
	// annotationProcessor는 별도 configuration이라 위 BOM이 적용되지 않음 -> platform을 따로 꽂음
	annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-batch-jdbc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
