# Use openjdk image on docker hub
FROM openjdk
MAINTAINER kurt

COPY server.jar server/
WORKDIR server/

# Run our program
ENTRYPOINT ["java", "-jar", "server.jar"]
