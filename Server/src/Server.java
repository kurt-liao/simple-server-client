import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.concurrent.*;

public class Server {

    private final static int PORT = 8080;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private final ThreadPoolExecutor clientThreadExecutor = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    public static HashMap<SelectionKey, ClientHandler> clientMap = new HashMap<SelectionKey, ClientHandler>();

    public static void main(String[] args) throws Throwable {
        // Use local host with port 8080
        InetSocketAddress listenAddress = new InetSocketAddress("localhost", PORT);
        System.out.println("Host:" + listenAddress.getAddress() + ", Port: " + listenAddress.getPort());

        new Server(listenAddress);
    }

    Server(InetSocketAddress listenAddress) throws Throwable {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.register(selector = Selector.open(), SelectionKey.OP_ACCEPT);
            serverChannel.bind(listenAddress);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        // Use a thread to check the key every 100ms.
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                selectKeys();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

    }

    /*
     * Use a loop to check channel key status
     *
     * If key is acceptable means new client is connected.
     * Register OP_READ key to that channel, and create new clienthandler instance to handle it.
     *
     * If key is readable means the existed client send a request to the server.
     * Get the clienthandler that matched in the clientmap and give it a thread to execute.
     */
    private void selectKeys() throws Throwable {
        selector.selectNow();
        for (SelectionKey key : selector.selectedKeys()) {
            try {
                if (!key.isValid())
                    continue;

                if (key.isAcceptable()) {
                    SocketChannel acceptedChannel = serverChannel.accept();
                    if (acceptedChannel == null)
                        continue;

                    acceptedChannel.configureBlocking(false);
                    SelectionKey readKey = acceptedChannel.register(selector, SelectionKey.OP_READ);
                    clientMap.put(readKey, new ClientHandler(readKey, acceptedChannel));

                    System.out.println("New client ip=" + acceptedChannel.getRemoteAddress() + ", total clients=" + Server.clientMap.size());
                }
                if (key.isReadable()) {
                    ClientHandler ch = clientMap.get(key);
                    if (ch == null)
                        continue;
                    clientThreadExecutor.execute(ch);
                }

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        selector.selectedKeys().clear();
    }
}
