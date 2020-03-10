# Use openjdk image on docker hub
FROM openjdk
MAINTAINER kurt

COPY Client.jar src /root/client/
WORKDIR /root/client/

# Run our program
ENTRYPOINT ["java", "-jar", "Client.jar"]
