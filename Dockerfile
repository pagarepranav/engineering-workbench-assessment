FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
RUN useradd --uid 10001 --create-home workbench && mkdir /app/data && chown -R workbench:workbench /app
COPY --from=build /build/target/engineering-workbench-1.0.0.jar /app/app.jar
USER workbench
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar","--server.address=0.0.0.0"]
