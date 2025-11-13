FROM eclipse-temurin:24


WORKDIR /app


COPY ./target/controller-0.0.1-SNAPSHOT.jar .


ENV PORT=9090


EXPOSE 9090

# Run the JAR
ENTRYPOINT ["java", "-jar", "controller-0.0.1-SNAPSHOT.jar"]
