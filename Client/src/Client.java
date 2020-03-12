import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.time.Duration;

public class Client {

    private static final int PORT = 8080;
    private SocketChannel socketChannel = null;
    private final ByteBuffer receiveBuffer;
    private Selector selector;

    Client(InetSocketAddress listenAddress) {
        this.receiveBuffer = ByteBuffer.allocate(1024);
        try {
            this.socketChannel = SocketChannel.open();
            this.socketChannel.connect(listenAddress);
            this.socketChannel.configureBlocking(false);
            this.selector = Selector.open();
            this.socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            System.out.println("Connection success...");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void sendMessageFromUser() {
        try {
            BufferedReader localReader = new BufferedReader(new InputStreamReader(System.in));
            String input = null;
            System.out.println("Try keyword: echo, time, quit. Enter \"close\" to close connection.");
            while ((input = localReader.readLine()) != null) {
                if (input.equals("close")) {
                    this.closeConnection();
                } else {
                    this.write(input);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listen2Keys() {
        try {
            while (this.selector.select() > 0) {
                Set<SelectionKey> keys = this.selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = null;
                    try {
                        key = (SelectionKey) it.next();
                        if (!key.isValid())
                            continue;

                        if (key.isReadable()) {
                            this.read(key);
                        }
                        it.remove();
                    } catch (Exception e) {
                        e.printStackTrace();
                        try {
                            assert key != null;
                            key.cancel();
                            key.channel().close();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.closeConnection();
        }
    }

    private void read(SelectionKey key) {
        StringBuilder receiveData = new StringBuilder();
        synchronized (this.receiveBuffer) {
            try {
                int amount_read = this.socketChannel.read(this.receiveBuffer);

                while (amount_read != 0) {
                    this.receiveBuffer.flip();
                    receiveData.append(StandardCharsets.UTF_8.decode(this.receiveBuffer));
                    this.receiveBuffer.clear();
                    amount_read = socketChannel.read(this.receiveBuffer);
                }

                if (receiveData.toString().equals("quit")) {
                    key.cancel();
                    this.closeConnection();
                }
            } catch (IOException io) {
                System.out.println("Server closed, can't read data.");
                this.closeConnection();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("Receive: " + receiveData.toString());
    }

    private void write(String input) {
        byte[] bArr = input.getBytes(StandardCharsets.UTF_8);
        ByteBuffer sendBuffer = ByteBuffer.wrap(bArr);
        try {
            this.socketChannel.write(sendBuffer);
            if (sendBuffer.hasRemaining()) {
                sendBuffer.compact();
            } else {
                sendBuffer.clear();
            }
        } catch (IOException io) {
            System.out.println("Server closed, can't write data.");
            this.closeConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeConnection() {
        try {
            this.socketChannel.close();
            System.out.println("Close connection...");
            this.selector.close();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    // Use for test
    // "-t" send time request 10 times
    // "-e" send echo request 10 times
    private void test(String type) {
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
            long startTime = System.nanoTime();
            for (int i = 0; i < 10; i++) {
                this.write(msg.toString());
                Thread.sleep(100);
            }
            long endTime = System.nanoTime();
            long elapseTime = endTime - startTime;
            System.out.println("Execution time: " + elapseTime);
            this.write(quitMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        int argLength = args.length;
        String testType = null;
        if (argLength == 1)
            testType = args[0];

        InetSocketAddress listenAddress = new InetSocketAddress("localhost", PORT);
        Client client = new Client(listenAddress);

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
                    client.sendMessageFromUser();
                }
            };
        }
        sender.start();
        client.listen2Keys();
    }
}
