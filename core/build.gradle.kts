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

    // @Entity, JpaRepository<T, ID> 가 core의 public 시그니처에 그대로 등장 -> api
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // GEOGRAPHY 컬럼이 매핑되는 JTS Point 타입도 엔티티 getter에 노출됨 -> api
    api("org.hibernate.orm:hibernate-spatial")

    // JDBC 드라이버는 코드에 타입으로 등장하지 않는 순수 런타임 구현체 -> runtimeOnly
    runtimeOnly("org.postgresql:postgresql")

    // Lombok: 컴파일 시점에만 getter/setter를 생성해주는 애노테이션 프로세서.
    // 생성된 getter는 core의 public 시그니처가 되지만, Lombok 자체 타입(@Getter 등)이
    // 컴파일된 클래스 파일에는 남지 않으므로 compileOnly로 충분 -> api/batch에 전파 안 됨.
    compileOnly("org.projectlombok:lombok")

    // annotationProcessor는 compileClasspath와 별개의 독립된 configuration이라
    // api(platform(...))의 BOM 제약이 전파되지 않음 -> 여기도 따로 platform을 꽂아줘야 함
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor("org.projectlombok:lombok")
}
