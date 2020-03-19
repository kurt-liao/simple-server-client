package nio.server;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


interface KeyState {
    int READ_STATE = 1;
    int WRITE_STATE = 2;
}

public class SelectionHandler implements Runnable{

    private final int state;
    private final SelectionKey key;

    SelectionHandler(int state, SelectionKey key) {
        this.state = state;
        this.key = key;
    }

    /**
     * Read the data from client channel.
     * @return String: The string that receive from channel.
     */
    private String read() {
        SocketChannel sc = (SocketChannel)key.channel();
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        StringBuilder receive = new StringBuilder();
        try {
            int amount_read = sc.read(readBuffer);

            while (amount_read != 0) {
                if (amount_read == -1) {
                    this.disconnectClient();
                    return "";
                }
                readBuffer.flip();

                CharBuffer cb = StandardCharsets.UTF_8.decode(readBuffer);
                receive.append(cb);
                readBuffer.clear();
                amount_read = sc.read(readBuffer);
            }
            System.out.println("Receive from " + sc.getRemoteAddress() + " >>> " + receive);
        } catch (Exception e) {
            this.disconnectClient();
        }
        return receive.toString();
    }


    /**
     * Write the data to client.
     */
    private void write() {
        SocketChannel sc = (SocketChannel) this.key.channel();
        try {
            if (!sc.isConnected()) {
                return;
            }
            ByteBuffer sendBuffer = (ByteBuffer)this.key.attachment(); // Get the buffer stored in the key.
            if (sendBuffer != null) {
                String send = new String(sendBuffer.array());
                System.out.println("Write to " + sc.getRemoteAddress() + " >>> " + send);
                sc.write(sendBuffer);
            }
        } catch (Exception e) {
            this.disconnectClient();
        }
    }


    /**
     * Parse the receive data after reading from the client channel.
     * @param receive : Received data from client channel.
     * @return boolean : False, if channel closed, and no need to write back to client. True, if parse success.
     */
    private boolean parseReceiveData(String receive) {
        SocketChannel sc = (SocketChannel) this.key.channel();
        if (!sc.isConnected())
            return false;

        StringBuilder error_msg = new StringBuilder();
        error_msg.append("Error input.\n");
        error_msg.append("\"time (GMT, CST...etc)\" to get current time\n");
        error_msg.append("\"echo (Hello...etc)\" to get echo response\n");
        error_msg.append("\"quit\" to close connection.\n");

        int firstSpace;
        String keyword = "";
        String response;
        String msg = "";

        // Get keyword. If not exist, return error message.
        if ((firstSpace = receive.indexOf(" ")) != -1) {
            keyword = receive.substring(0, firstSpace);
            msg = receive.substring(firstSpace + 1, receive.length());
        } else {
            if (receive.equals("quit")) {
                keyword = receive;
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

        ByteBuffer bb = ByteBuffer.wrap(response.getBytes());
        this.key.attach(bb);     // Put the data buffer into key.
        return true;
    }


    /**
     * Close the client channel.
     */
    private void disconnectClient() {
        SocketChannel sc = (SocketChannel) this.key.channel();
        try {
            if (sc != null && sc.isConnected()) {
                System.out.println("Close client " + sc.getRemoteAddress());
                sc.close();
            }
        } catch (Exception e) {
            System.out.println("Close client connection failed. " + e.getMessage());
        }
    }


    /**
     * Get the current time with matched timezone.
     * @param zoneId : The string that determined the zone.
     * @return : String of time with the pattern "yyyy-MMM-dd HH:mm:ss zzz".
     */
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


    public void run() {
        System.out.println(Thread.currentThread().getName() + " start.");
        switch (this.state) {
            case KeyState.READ_STATE:
                String receive = this.read();
                boolean bRet = this.parseReceiveData(receive);
                if (bRet)
                    this.key.interestOps(this.key.interestOps() | SelectionKey.OP_WRITE);
                break;

            case KeyState.WRITE_STATE:
                SocketChannel sc = (SocketChannel) this.key.channel();
                this.write();
                if (sc.isConnected())
                    this.key.interestOps(this.key.interestOps() | SelectionKey.OP_READ); // after write action, ready to read.
                break;
        }
        System.out.println(Thread.currentThread().getName() + " end.");
    }
}
