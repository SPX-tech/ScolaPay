FROM eclipse-temurin:25.0.5_8-jdk-alpine-3.24 AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:25.0.5_8-jre-alpine-3.24
RUN addgroup -S scolapay && adduser -S scolapay -G scolapay
WORKDIR /app
COPY --from=build /app/target/scolapay-core.jar app.jar
USER scolapay
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
