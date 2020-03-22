package  nio.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;

public class NioServer {

    private final InetAddress address;
    private final int port;
    private Selector selector = null;
    private ServerSocketChannel serverChannel = null;
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 5, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100));
    public final HashMap<SelectionKey, LinkedList<String>> keyMap = new HashMap<>();


    NioServer(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }


    /**
     * Open serverChannel with non-blocking mode, and bind to the address.
     * Then, open selector and register ACCEPT event to the serverChannel.
     * Finally, listen the key event in a loop until server shutdown.
     */
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
                this.selector.selectNow();
                for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    SelectionKey key = it.next();
                    it.remove();

                    if (!key.isValid())
                        continue;

                    if (key.isAcceptable()) {
                        this.accept();
                    } else if (key.isReadable()) {
                        key.interestOps(0);
                        ClientHandler ch = new ClientHandler(KeyState.READ_STATE, key, this.keyMap);
                        this.pool.execute(ch);
                    } else if (key.isWritable()) {
                        key.interestOps(0);
                        ClientHandler ch = new ClientHandler(KeyState.WRITE_STATE, key, this.keyMap);
                        this.pool.execute(ch);
                    }
                }
            }
        } catch (Exception e){
            System.out.println("Get error: " + e.getMessage());
        } finally {
            this.shutdownServer();
        }
    }


    /**
     * Accept the client channel with non-blocking mode.
     * Write the "Hello" message to the client.
     */
    private void accept() {
        try {
            SocketChannel sc = this.serverChannel.accept();
            sc.configureBlocking(false);
            sc.register(this.selector, SelectionKey.OP_READ);
            System.out.println("New client " + sc.getRemoteAddress() + " is connect..");
            String response = "Hello " + sc.getRemoteAddress() + "..";
            sc.write(ByteBuffer.wrap(response.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Close the threadPool, serverChannel, selector.
     */
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
