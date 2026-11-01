# ---- build ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
# prima solo il pom: le dipendenze restano in cache finché il pom non cambia
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 motoride
COPY --from=build /build/target/motorbike-*.jar app.jar
USER motoride
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
