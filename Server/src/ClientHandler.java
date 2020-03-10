import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

class ClientHandler implements Runnable{
    private final SelectionKey selKey;
    private final SocketChannel client;
    private final ByteBuffer readBuffer;
    private ByteBuffer sendBuffer;

    ClientHandler(SelectionKey selKey, SocketChannel client) throws Throwable {
        this.selKey = selKey;
        this.client = (SocketChannel) client.configureBlocking(false); // non-blocking
        readBuffer = ByteBuffer.allocateDirect(1024);
        sendBuffer = ByteBuffer.allocateDirect(1024);
    }

    private void disconnect() {
        Server.clientMap.remove(selKey);
        try {
            if (selKey != null)
                selKey.cancel();

            if (client == null)
                return;

            System.out.println("Client " + client.getRemoteAddress() + " closed.");
            client.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private String readFromClient() {
        synchronized (readBuffer) {
            StringBuilder receiveData = new StringBuilder();
            int count = -1;
            try {
                count = client.read(readBuffer);

                if (count == -1) {
                    // client closed
                    disconnect();
                    return "";
                }
                while (count != 0) {
                    readBuffer.flip();
                    receiveData.append(decode(readBuffer));
                    readBuffer.clear();
                    count = client.read(readBuffer);
                }
            } catch (Throwable t) {
                disconnect();
                t.printStackTrace();
                return "";
            }
            System.out.println("Receive:" + receiveData);

            return receiveData.toString();
        }
    }

    /**
     * Parse content of request and return response it matched.
     * @param  (receiveData) A string that read from client.
     * @return Return a response string that need to be send back to client.
     */
    private String parseRequestContent(String receiveData) {
        int firstSpace = 0;
        String keyword = "";
        String msg = "";
        String ERROR_INPUT = "Error input. 'time (GMT, CST...etc)' to get current time\n'echo (Hello...etc)' to get echo response,\n'quit' to close connection.";
        String response = "";

        // Get keyword. If not exist, return error input string.
        if ((firstSpace = receiveData.indexOf(" ")) != -1) {
            keyword = receiveData.substring(0, firstSpace);
            msg = receiveData.substring(firstSpace + 1, receiveData.length());
        } else {
            if (receiveData.equals("quit")) {
                keyword = receiveData;
            } else {
                return ERROR_INPUT;
            }
        }
        switch (keyword) {
            case "echo":
                response = msg;
                break;
            case "time":
                response = getCurrentTime(msg);
                break;
            case "quit":
                response = "quit";
                break;
            default:
                response = ERROR_INPUT;
                break;
        }
        System.out.println("Response: " + response);
        return response;
    }

    private void sendResponse(String response) {
        System.out.println("Send: " + response);
        int offset = 0;
        int length = 0;

        byte[] b = response.getBytes(StandardCharsets.UTF_8);

        synchronized (sendBuffer) {
            int remainMessageLength = b.length;
            while (offset < b.length) {
                if (remainMessageLength > sendBuffer.capacity()) {
                    length = sendBuffer.capacity();
                    remainMessageLength -= length;
                } else {
                    length = remainMessageLength;
                }
                sendBuffer = ByteBuffer.wrap(b, offset, length);
                offset += length;
                try {
                    client.write(sendBuffer);
                    if (sendBuffer.hasRemaining()) {
                        sendBuffer.compact();
                    } else {
                        sendBuffer.clear();
                    }
                } catch (Throwable t) {
                    disconnect();
                    t.printStackTrace();
                }
            }
        }
    }

    private String getCurrentTime(String timeZone){
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss zzz");
        // Default is GMT
        formatter.setTimeZone(TimeZone.getTimeZone(timeZone));
        return formatter.format(date);
    }

    private String decode(ByteBuffer buffer) {
        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        return charBuffer.toString();
    }

    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + " start work.");
        String receive = readFromClient();
        String response = parseRequestContent(receive);
        if (client.isConnected())
            sendResponse(response);
        System.out.println(Thread.currentThread().getName() + " end work.");
    }
}