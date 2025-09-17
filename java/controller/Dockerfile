
FROM openjdk:24

COPY ./target/controller-0.0.1-SNAPSHOT.jar /app/controller-0.0.1-SNAPSHOT.jar

WORKDIR /app

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/app/controller-0.0.1-SNAPSHOT.jar"]





