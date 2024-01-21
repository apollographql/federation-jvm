FROM openjdk:17.0.2

EXPOSE 4001
RUN mkdir /app

COPY build/libs/federation-jvm-compatibility.jar /app/app.jar

ENTRYPOINT ["java", "-jar","/app/app.jar"]
