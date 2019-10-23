FROM us.gcr.io/ppp-prod/common:java-8-latest

COPY target/review-fed-app.jar /opt/app/app.jar
WORKDIR /opt/app/
ENTRYPOINT java $JAVA_OPTS -jar ./app.jar
EXPOSE 4002
