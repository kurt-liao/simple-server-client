package nio.client;

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

public class NioClient {

    private static final int PORT = 8080;
    private SocketChannel socketChannel = null;
    private final ByteBuffer receiveBuffer;
    private Selector selector;

    NioClient(InetSocketAddress listenAddress) {
        this.receiveBuffer = ByteBuffer.allocate(1024);
        try {
            this.socketChannel = SocketChannel.open();
            System.out.println("Channel open success..");
            this.socketChannel.configureBlocking(false);

            this.selector = Selector.open();
            System.out.println("Selector open success..");

            this.socketChannel.connect(listenAddress);
            this.socketChannel.register(selector, SelectionKey.OP_CONNECT);
        } catch (Exception e) {
            System.out.println("Channel open failed.." + e.getMessage());
            System.exit(-1);
        }
    }

    public void listenKeys() {
        try {
            while (this.selector.isOpen()) {
                this.selector.select();
                for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    SelectionKey key = null;
                    key = (SelectionKey) it.next();
                    it.remove();

                    if (!key.isValid())
                        continue;

                    if (key.isConnectable()) {
                        this.socketChannel = (SocketChannel) key.channel();
                        // if still connecting, complete connection.
                        if (this.socketChannel.isConnectionPending()) {
                            System.out.println("connection pending..");
                            this.socketChannel.finishConnect();
                            System.out.println("finish connect..");
                        }
                        this.socketChannel.register(this.selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        this.read(key);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("key error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            this.closeConnection();
        }
    }

    private void read(SelectionKey key) {
        StringBuilder receiveData = new StringBuilder();
        try {
            int amount_read = this.socketChannel.read(this.receiveBuffer);

            while (amount_read != 0) {
                this.receiveBuffer.flip();
                receiveData.append(StandardCharsets.UTF_8.decode(this.receiveBuffer));
                this.receiveBuffer.clear();
                amount_read = socketChannel.read(this.receiveBuffer);
            }
            System.out.println("Receive: " + receiveData.toString());

            if (receiveData.toString().equals("quit")) {
                key.cancel();
                this.closeConnection();
            }
        } catch (IOException io) {
            System.out.println("Server closed, can't read data.");
            this.closeConnection();
        } catch (Exception e) {
            System.out.println("Read data failed.\n" + e.getMessage());
        }
    }

    private void write(String input) {
        byte[] bArr = input.getBytes(StandardCharsets.UTF_8);
        ByteBuffer sendBuffer = ByteBuffer.wrap(bArr);
        try {
            if (this.socketChannel.isConnected()) {
                this.socketChannel.write(sendBuffer);
                if (sendBuffer.hasRemaining()) {
                    sendBuffer.compact();
                } else {
                    sendBuffer.clear();
                }
            }
        } catch (IOException io) {
            System.out.println("Server closed, can't write data.");
            this.closeConnection();
        } catch (Exception e) {
            System.out.println("Write data failed.\n" + e.getMessage());
        }
    }

    private void closeConnection() {
        try {
            this.socketChannel.close();
            System.out.println("Close connection...");
            this.selector.close();
            System.exit(0);
        } catch (Exception e) {
            System.out.println("Close connection failed.\n" + e.getMessage());
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
                    input += '\n';
                    this.write(input);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Use for test
     * @param type  "-t" send time request 5 times
     *              "-e" send echo request 5 times
     *              otherwise, default is echo command too.
     */
    private void test(String type) {
        String quitMsg = "quit\n";
        StringBuilder msg;
        switch (type) {
            case "-t":
                msg = new StringBuilder("time GMT\n");
                break;
            case "-e":
                msg = new StringBuilder("echo Hello, it's test.\n");
                break;
            default:
                msg = new StringBuilder("echo Hello, it's default.\n");
                break;
        }
        try {
            for (int i = 0; i < 5; i++) {
                this.write(msg.toString());
                Thread.sleep(500);
            }
            this.write(quitMsg);
        } catch (Exception e) {
            System.out.println("Test error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int argLength = args.length;
        String testType = null;
        if (argLength == 1)
            testType = args[0];

        InetSocketAddress listenAddress = new InetSocketAddress("localhost", PORT);
        NioClient client = new NioClient(listenAddress);

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
        client.listenKeys();
    }
}