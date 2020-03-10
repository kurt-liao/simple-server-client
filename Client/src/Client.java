import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

public class Client {
    private static final int PORT = 8080;
    private SocketChannel socketChannel = null;
    private final ByteBuffer sendBuffer;
    private final ByteBuffer receiveBuffer;
    private final Charset charset = StandardCharsets.UTF_8;
    private Selector selector;

    Client(InetSocketAddress listenAddress) throws IOException {
        sendBuffer = ByteBuffer.allocate(1024);
        receiveBuffer = ByteBuffer.allocate(1024);
        socketChannel = SocketChannel.open();

        try {
            socketChannel.connect(listenAddress);
            socketChannel.configureBlocking(false);
            System.out.println("Connection success...");
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Throwable {
        int argLength = args.length;
        String testType = null;
        if (argLength == 1)
            testType = args[0];

        InetSocketAddress listenAddress = new InetSocketAddress("localhost", PORT);
        final Client client = new Client(listenAddress);
        Thread sender;
        if (testType != null) {
            String type = testType;
            sender = new Thread() {
                    public void run() {
                        client.test(type);
                    }
                };
        } else {
            sender = new Thread() {
                    public void run() {
                        client.sendMessageFromInput();
                    }
                };
        }
        sender.start();
        client.listen2KeyReadable();
    }

    private void sendMessageFromInput() {
        try {
            BufferedReader localReader = new BufferedReader(new InputStreamReader(System.in));
            String msg = null;
            System.out.println("Try keyword: echo, time, quit. Enter \"close\" to close connection.");
            while ((msg = localReader.readLine()) != null) {
                if (msg.equals("close"))
                    closeConnection();
                else
                    send(msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void send(String msg) throws IOException {
        synchronized (sendBuffer) {
            byte[] b = msg.getBytes(StandardCharsets.UTF_8);
            int offset = 0;
            int length = 0;
            int remainMessageLength = b.length;
            while (offset < b.length) {
                if (remainMessageLength > sendBuffer.capacity()) {
                    length = sendBuffer.capacity();
                    remainMessageLength -= length;
                } else {
                    length = remainMessageLength;
                }
                sendBuffer.put(ByteBuffer.wrap(b, offset, length));
                offset += length;

                sendBuffer.flip();

                try {
                    socketChannel.write(sendBuffer);
                    if (sendBuffer.hasRemaining()) {
                        sendBuffer.compact();
                    } else {
                        sendBuffer.clear();
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    private void listen2KeyReadable() throws IOException {
        try {
            while (selector.select() > 0) {
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = readyKeys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = null;
                    try {
                        key = (SelectionKey) it.next();
                        if (!key.isValid())
                            continue;
                        // If it's readable, get the data and print.
                        if (key.isReadable()) {
                            String receive = getReceiveData(key);
                            System.out.println(receive);
                        }
                        it.remove();
                    } catch (IOException e) {
                        e.printStackTrace();
                        try {
                            key.cancel();
                            key.channel().close();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getReceiveData(SelectionKey key) throws IOException {
        StringBuilder receiveData = new StringBuilder();
        int count = socketChannel.read(receiveBuffer);

        while (count != 0) {
            receiveBuffer.flip();
            receiveData.append(decode(receiveBuffer));
            receiveBuffer.clear();
            count = socketChannel.read(receiveBuffer);
        }

        if (receiveData.toString().equals("quit")) {
            key.cancel();
            closeConnection();
        }
        return receiveData.toString();
    }

    private void closeConnection() throws IOException {
        socketChannel.close();
        System.out.println("Close connection...");
        selector.close();
        System.exit(0);
    }

    private String decode(ByteBuffer buffer) {
        CharBuffer charBuffer = charset.decode(buffer);
        return charBuffer.toString();
    }

    private ByteBuffer encode(String str) {
        return charset.encode(str);
    }

    // Use for test
    // "-t" send time request 10 times
    // "-e" send echo request 10 times
    private void test(String type){
        String quitMsg = "quit";
        StringBuilder msg;
        switch (type) {
            case "-t":
                msg = new StringBuilder("time GMT+8");
                break;
            case "-e":
                msg = new StringBuilder("echo Hello, it's test.");
                break;
            default:
                msg = new StringBuilder("echo Hello, it's default.");
                break;
        }
        try {
            for (int i = 0; i < 10; i++) {
                send(msg.toString());
                Thread.sleep(500);
            }
            // send quit to close connection
            send(quitMsg);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}