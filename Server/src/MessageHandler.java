package nio.server;

import java.nio.channels.SelectionKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Queue;

public class MessageHandler implements Runnable{

    private final Queue<Message> messages;
    private final String receive;
    private final SelectionKey key;

    MessageHandler(Queue<Message> messages, String receive, SelectionKey key) {
        this.messages = messages;
        this.receive = receive;
        this.key = key;
    }

    private Message parseReceiveData() {
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
        if ((firstSpace = this.receive.indexOf(" ")) != -1) {
            keyword = this.receive.substring(0, firstSpace);
            msg = this.receive.substring(firstSpace + 1, this.receive.length());
        } else {
            if (this.receive.equals("quit")) {
                keyword = this.receive;
            } else {
                keyword = error_msg.toString();
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
        return new Message(this.key, keyword, response);
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

    public void run() {
        try {
            System.out.println(Thread.currentThread().getName() + " start.");
            synchronized (this.messages) {
                Message newMsg = parseReceiveData();
                boolean bRet = this.messages.offer(newMsg);
                if (!bRet) {
                    System.out.println("Cannot add the message to the queue.");
                } else {
                    System.out.println("Add new message item, now size: " + this.messages.size());
                }

                if (this.key.isValid()) {
                    this.key.interestOps(SelectionKey.OP_WRITE);
                }

                System.out.println(Thread.currentThread().getName() + " end.");
            }
        } catch (Exception e) {
            System.out.println("Error occurred in " + Thread.currentThread().getName() + "\n" + e.getMessage());
        }
    }
}
