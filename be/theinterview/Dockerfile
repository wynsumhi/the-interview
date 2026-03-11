# 1. Build Stage
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 종속성 파일들만 먼저 복사해서 캐싱 활용
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew
# 라이브러리 먼저 다운로드
RUN ./gradlew dependencies --no-daemon

# 그 다음 소스 코드 복사 후 빌드
COPY . .
RUN ./gradlew bootJar -x test --no-daemon

# 2. Run Stage
FROM eclipse-temurin:17-jre
EXPOSE 8080
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]