FROM tomcat:9-jdk17-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY target/independence-backend.war /usr/local/tomcat/webapps/ROOT.war

ENV TZ=Asia/Seoul
EXPOSE 8080
