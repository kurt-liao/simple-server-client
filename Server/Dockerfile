# Use openjdk image on docker hub
FROM openjdk
MAINTAINER kurt

COPY Server.jar src /root/server/
WORKDIR /root/server/


# Run our program
ENTRYPOINT ["java", "-jar", "Server.jar"]
