# Project Title

It is a simple server-client project with java nio.
You can run the program by jar file, or build it in the docker and run it.

## Getting Started

Clone the project

### Prerequisites

Install docker(sample in ubuntu)

```
sudo apt-get install docker.io
```

### Build Docker Image

Get into simple-client-server directory

```
cd simple-client-server
```

Get into the Server directory

```
cd Server
```

Build the image use docker build

```
docker build -t server .
```

Get into Client directory and build client image as well.

```
docker build -t client .
```

Use docker images command to check if the images(server, client) are success build.

```
docker images
```


## Running the program

Run the server by docker run command (use localhost)

```
docker run -it --net=host server
```


After running the server, run the client. 

```
docker run -it --net=host client
```

&nbsp;
After running the client, you can enter the command like "echo hello", and the server will return "hello" back to you.

Command could be: 
>echo xxx (e.g echo Hello)
>time xxx (e.g time GMT)
>quit    (close connection)
tips: You can also type "close" for close connection immediately.(It won't send anything to server.)

&nbsp;
&nbsp;
If you want to run the quick test for client, use the command below.
"-t" means sending message is "time GMT+8"
"-e" means sending message is "echo Hello, it's test."

```
docker run -it --net=host client [-t/-e]
```
