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
import java.util.*;


interface KeyState {
    int READ_STATE = 1;
    int WRITE_STATE = 2;
}

public class ClientHandler implements Runnable{

    private final int state;
    private final SelectionKey key;
    private final HashMap<SelectionKey, LinkedList<String>> keyMap;

    ClientHandler(int state, SelectionKey key, HashMap<SelectionKey, LinkedList<String>> keyMap) {
        this.state = state;
        this.key = key;
        this.keyMap = keyMap;
    }

    /**
     * Read the data from client channel.
     * @return String: The string that receive from channel.
     */
    private String read() {
        SocketChannel sc = (SocketChannel)this.key.channel();
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
     * Then, set the key interest write if there are messages left, or set it read.
     */
    private void write() {
        try {
            if (!this.keyMap.containsKey(this.key))
                return;

            SocketChannel sc = (SocketChannel) this.key.channel();
            if (!sc.isConnected())
                return;

            LinkedList<String> list = this.keyMap.get(this.key);
            String msg = list.poll();

            if (msg != null) {
                ByteBuffer sendBuffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
                String send = new String(sendBuffer.array());
                System.out.println("Write to " + sc.getRemoteAddress() + " >>> " + send);
                sc.write(sendBuffer);
            }

            // If message has remain, this key will inerestOps on write. Otherwise, on read.
            if (!list.isEmpty()) {
                System.out.println("Something left to write: " + list.size());
                this.key.interestOps(SelectionKey.OP_WRITE);
            } else {
                this.key.interestOps(SelectionKey.OP_READ);
            }
        } catch (Exception e) {
            this.disconnectClient();
        }
    }


    /**
     * Parse the receive data after reading from the client channel and add into keyMap.
     * @param receive : Received data from client channel.
     * @return boolean : False, if channel closed, and no need to write back to client. True, if parse success.
     */
    private int parseReceiveData(String receive) {
        SocketChannel sc = (SocketChannel) this.key.channel();
        if (!sc.isConnected())
            return -1;

        StringBuilder error_msg = new StringBuilder();
        error_msg.append("Error input.\n");
        error_msg.append("\"time (GMT, CST...etc)\" to get current time\n");
        error_msg.append("\"echo (Hello...etc)\" to get echo response\n");
        error_msg.append("\"quit\" to close connection.\n");

        int firstSpace;
        String keyword = "";
        String response;
        String msg = "";


        int newLine = receive.indexOf('\n');

        if (newLine == - 1) { // Input string not in correct term.
            response = error_msg.toString();
        } else {
            // Get keyword. If not exist, return error message.
            String newReceive = receive.substring(0, newLine);

            if ((firstSpace = newReceive.indexOf(" ")) != -1) {
                keyword = newReceive.substring(0, firstSpace);
                msg = newReceive.substring(firstSpace + 1, newLine);
            } else {
                if (newReceive.equals("quit")) {
                    keyword = newReceive;
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
        }

        LinkedList<String> list;
        if (!this.keyMap.containsKey(this.key)) {
            list = new LinkedList<String>();
        } else {
            list = this.keyMap.get(this.key);
        }
        list.add(response);
        this.keyMap.put(this.key, list);

        // Check if there is message left.
        // If true, return the int of next index of newline.
        // Otherwise, return -1.
        if (newLine == receive.length() -1)
            return -1;
        else
            return newLine + 1;
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
        synchronized (this.keyMap) {
            try {
                System.out.println(Thread.currentThread().getName() + " start.");
                SocketChannel sc = (SocketChannel) this.key.channel();
                switch (this.state) {
                    case KeyState.READ_STATE:
                        Thread.sleep(5000); // Sleep 2s for testing blocking.
                        String receive = this.read();

                        // Not equal to -1 means message left, need to parse again.
                        int next = this.parseReceiveData(receive);
                        while (next != -1) {
                            receive = receive.substring(next);
                            System.out.println("Something need to read...");
                            next = this.parseReceiveData(receive);
                        }
                        if (sc.isConnected())
                            this.key.interestOps(this.key.interestOps() | SelectionKey.OP_WRITE);
                        break;

                    case KeyState.WRITE_STATE:
                        this.write();
                        break;
                }
                System.out.println(Thread.currentThread().getName() + " end.");
            } catch (Exception e) {
                System.out.println(Thread.currentThread().getName() + " get error: " + e.getMessage());
            }
        }
    }
}
