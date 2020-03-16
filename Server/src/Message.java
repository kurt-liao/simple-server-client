package nio.server;
import java.nio.channels.SelectionKey;

class Message {
    private final SelectionKey key;
    private final String keyword;
    private final String msg;

    Message(SelectionKey key, String keyword, String msg) {
        this.key = key;
        this.keyword = keyword;
        this.msg = msg;
    }

    public SelectionKey getKey() {
        return this.key;
    }

    public String getKeyword() {
        return this.keyword;
    }

    public String getMsg() {
        return this.msg;
    }
}
