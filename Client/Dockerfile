# Use openjdk image on docker hub
FROM openjdk
MAINTAINER kurt

COPY client.jar client/
WORKDIR client/

# Run our program
ENTRYPOINT ["java", "-jar", "client.jar"]
