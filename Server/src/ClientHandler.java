import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;

class ClientHandler implements Runnable{
    private SelectionKey selKey;
    private SocketChannel client;
    private final ByteBuffer receiveBuffer;

    ClientHandler(SelectionKey selKey, SocketChannel client) {
        this.receiveBuffer = ByteBuffer.allocate(1024);
        try {
            this.selKey = selKey;
            this.client = (SocketChannel) client.configureBlocking(false); // non-blocking
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disconnect() {
        // Remove the disconnected object from map
        Server.clientMap.remove(this.selKey);
        try {
            if (this.client == null)
                return;
            if (this.selKey != null)
                this.selKey.cancel();

            if (this.client.isConnected()) {
                System.out.println("Client " + this.client.getRemoteAddress() + " closed.");
                this.client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String readFromClient() {
        StringBuilder receiveData = new StringBuilder();
        synchronized (this.receiveBuffer) {
            int amount_read = -1;
            try {
                amount_read = this.client.read(this.receiveBuffer);

                if (amount_read == -1) {
                    this.disconnect();
                    return "";
                }
                while (amount_read != 0) {
                    this.receiveBuffer.flip();
                    receiveData.append(StandardCharsets.UTF_8.decode(this.receiveBuffer));
                    this.receiveBuffer.clear();
                    amount_read = this.client.read(this.receiveBuffer);
                }
                System.out.println("From(" +  this.client.getRemoteAddress() + ") Receive: " + receiveData);
            } catch (Exception e) {
                e.printStackTrace();
                this.disconnect();
            }

            return receiveData.toString();
        }
    }

    /**
     * Parse content of request and return response it matched.
     * @param  (receiveData) A string that read from client.
     * @return Return a response string that need to be send back to client.
     */
    private String parseReceiveContent(String receiveData) {
        StringBuilder error_msg = new StringBuilder();
        error_msg.append("Error input.\n");
        error_msg.append("\"time (GMT, CST...etc)\" to get current time\n");
        error_msg.append("\"echo (Hello...etc)\" to get echo response\n");
        error_msg.append("\"quit\" to close connection.\n");

        int firstSpace;
        String keyword;
        String response;
        String msg = "";

        // Get keyword. If not exist, return error input string.
        if ((firstSpace = receiveData.indexOf(" ")) != -1) {
            keyword = receiveData.substring(0, firstSpace);
            msg = receiveData.substring(firstSpace + 1, receiveData.length());
        } else {
            if (receiveData.equals("quit")) {
                keyword = receiveData;
            } else {
                return error_msg.toString();
            }
        }
        switch (keyword) {
            case "echo":
                response = msg;
                break;
            case "time":
                response = this.getCurrentTime(msg);
                break;
            case "quit":
                response = "quit";
                break;
            default:
                response = error_msg.toString();
                break;
        }
        return response;
    }

    private void sendResponse(String response) {
        byte[] bArr = response.getBytes(StandardCharsets.UTF_8);
        ByteBuffer sendBuffer = ByteBuffer.wrap(bArr);

        try {
            System.out.println("Send(" + this.client.getRemoteAddress() + "): "+ response);
            this.client.write(sendBuffer);
            if (sendBuffer.hasRemaining()) {
                sendBuffer.compact();
            } else {
                sendBuffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.disconnect();
        }
        selKey.interestOps(SelectionKey.OP_READ);
    }

    private String getCurrentTime(String zoneId){
        ZonedDateTime now;
        String id = zoneId;

        if (ZoneId.SHORT_IDS.containsKey(zoneId)) {
            id = ZoneId.SHORT_IDS.get(zoneId);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss zzz");
        try {
            now = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of(id));
        } catch (Exception e) {
            System.out.println("Cannot convert this id, use default.");
            now = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        }
        return now.format(formatter);
    }

    @Override
    public void run() {
        try {
            System.out.println(Thread.currentThread().getName() + " start work.");
            String receive = this.readFromClient();
            String response = this.parseReceiveContent(receive);
            if (this.client.isConnected())
                this.sendResponse(response);
            System.out.println(Thread.currentThread().getName() + " end work.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
