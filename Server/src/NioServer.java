package  nio.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

public class NioServer {

    private final InetAddress address;
    private final int port;
    private Selector selector = null;
    private ServerSocketChannel serverChannel = null;
    private final Queue<Message> messages = new LinkedList<>();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 5, 1L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));

    NioServer(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    public void startServer() {
        try {
            InetSocketAddress listenAddress = new InetSocketAddress(this.address, this.port);
            this.serverChannel = ServerSocketChannel.open();
            this.serverChannel.socket().bind(listenAddress);
            this.serverChannel.configureBlocking(false);
            this.selector = Selector.open();
            this.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
            System.out.println("Server start on port " + this.port + "...");

            while (this.selector.isOpen()) {
                this.selector.select(50);
                for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (!key.isValid())
                        continue;

                    if (key.isAcceptable()) {
                        SocketChannel sc = this.serverChannel.accept();
                        sc.configureBlocking(false);
                        sc.register(this.selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        System.out.println("New client " + sc.getRemoteAddress() + " is connect..");
                        this.accept(sc);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }
            }
        } catch (Exception e){
            System.out.println("Get error: " + e.getMessage());
        } finally {
            this.shutdownServer();
        }
    }

    private void accept(SocketChannel sc) {
        try {
            String response = "Hello " + sc.getRemoteAddress() + "..";
            sc.write(ByteBuffer.wrap(response.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void read(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        StringBuilder receive = new StringBuilder();
        try {
            int amount_read = sc.read(readBuffer);

            while (amount_read != 0) {
                // client closed..
                if (amount_read == -1) {
                    this.disconnectClient(key);
                    return;
                }
                readBuffer.flip();
                receive.append(StandardCharsets.UTF_8.decode(readBuffer));
                readBuffer.clear();
                amount_read = sc.read(readBuffer);
            }
            System.out.println("Receive from " + sc.getRemoteAddress() + " >>> " + receive);
            // Get request data, use another thread to parse it.
            this.pool.execute(new MessageHandler(this.messages, receive.toString(), key));
        } catch (Exception e) {
            this.disconnectClient(key);
        }
    }

    private void write(SelectionKey key) {
        try {
            synchronized (this.messages) {
                if (this.messages.isEmpty()) {
                    key.interestOps(SelectionKey.OP_READ);
                    return;
                }

                Message msg = this.messages.poll();
                if (msg != null) {
                    SocketChannel sc = (SocketChannel) msg.getKey().channel();
                    if (!sc.isOpen()) {
                        return;
                    }

                    String send = msg.getMsg();
                    ByteBuffer sendBuffer = ByteBuffer.wrap(send.getBytes(StandardCharsets.UTF_8));

                    System.out.println("Write to " + sc.getRemoteAddress() + " >>> " + msg.getMsg());
                    sc.write(sendBuffer);
                }
                key.interestOps(SelectionKey.OP_READ);
            }
        } catch (Exception e) {
            this.disconnectClient(key);
        }
    }

    private void disconnectClient(SelectionKey key) {
        SocketChannel sc = (SocketChannel)key.channel();
        try {
            if (sc != null && sc.isConnected()) {
                System.out.println("Close client " + sc.getRemoteAddress());
                sc.close();
            }
        } catch (Exception e) {
            System.out.println("Close client connection failed. " + e.getMessage());
        }
    }

    private void shutdownServer() {
        try {
            this.pool.shutdown();
            if (this.selector.isOpen() && this.selector != null) {
                this.selector.close();
            }
            if (this.serverChannel.isOpen() && this.serverChannel != null) {
                this.serverChannel.close();
            }
            System.out.println("Shutdown server..");
        } catch (Exception e) {
            System.out.println("Close server failed.\n" + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new NioServer(null, 8080).startServer();
    }
}
