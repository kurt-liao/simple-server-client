# Project Title

It is a simple server-client project with java nio.
You can run the program by jar file, or build it in the docker and run it.

## Getting Started

Clone the repository.

### Prerequisites

Install docker(sample in ubuntu)
```
sudo apt-get install docker.io
```

### Build Docker Image

Get into simple-server-client directory
```
cd simple-server-client
```

Get into the server directory
```
cd server
```

Build the image use docker build
```
docker build -t nio_server .
```


Get into client directory and build it as well.
```
docker build -t nio_client .
```

Use docker images command to check if the images(server, client) are success build.
```
docker images
```


## Running the program

Run the server by docker run command
```
docker run --net=host server
```


After running the server, run the client.
```
docker run -it --net=host nio_client
```

&nbsp;
After running the client, you can enter the command like "echo hello", and the server will return "hello" back to you.

Command could be : 
>* echo xxx (e.g echo Hello)
>* time xxx (e.g time America/Chicago)
>* quit

_Tips : You can also type "close" for close connection immediately.(It won't send anything to server.)_

&nbsp;
If you want to run the quick test for client, use the command below.
"-t" means sending message is "time GMT"
"-e" means sending message is "echo Hello, it's test."

```
docker run -it --net=host client [-t/-e]
```
&nbsp;

# Run multi-clients

The reason that we might have multiple clients, so we use docker-compose command to create multiple clients(containers) and run it.

## Install docker-compose

```
curl -L https://github.com/docker/compose/releases/download/1.8.1/docker-compose-`uname -s`-`uname -m` > /usr/local/bin/docker-compose 
```
or, if you have python-pip.

```
pip3 install docker-compose
```

## Run the multi-clients

Use "docker-compose up" command with keyword "scale"

```
docker-compose up --scale client=10
```

Run above command, and you can get 10 clients. Enter the number of clients that you want to create.

We can only run the test command if you want the clients to have some interactive with server because we only have a terminal, the command that enter in will not work.

You can modify the command in docker-compose.yml file to try different test action(-t or -e), or empty command for just connecting to server.

![](https://i.imgur.com/9sfv0ge.jpg)

