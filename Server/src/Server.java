import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.concurrent.*;

public class Server {

    private final InetAddress addr;
    private final int port;
    private ServerSocketChannel serverChannel = null;
    private Selector selector = null;
    private final ThreadPoolExecutor clientThreadExecutor = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    public static HashMap<SelectionKey, ClientHandler> clientMap = new HashMap<SelectionKey, ClientHandler>();

    public Server(InetAddress addr, int port) {
        this.addr = addr;
        this.port = port;
        this.startServer();
    }

    private void startServer() {
        try {
            InetSocketAddress listenAddr = new InetSocketAddress(this.addr, this.port);
            this.serverChannel = ServerSocketChannel.open();
            this.serverChannel.configureBlocking(false);
            this.serverChannel.bind(listenAddr);
            this.serverChannel.register(this.selector = Selector.open(), SelectionKey.OP_ACCEPT);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Open server channel failed.");
            System.exit(-1);
        }
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                this.listen2Keys();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0,100 , TimeUnit.MILLISECONDS);

    }

    /**
     * Use a loop to check channel key status
     *
     * If key is acceptable means new client is connected.
     * Register OP_READ key to that channel, and create new clienthandler instance to handle it.
     *
     * If key is readable means the existed client send a request to the server.
     * Get the clienthandler that matched in the clientmap and give it a thread to execute.
     */
    private void  listen2Keys() {
        try {
            this.selector.selectNow();
            for (SelectionKey key : this.selector.selectedKeys()) {
                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    SocketChannel acceptedChannel = this.serverChannel.accept();
                    if (acceptedChannel == null)
                        continue;

                    acceptedChannel.configureBlocking(false);
                    SelectionKey readKey = acceptedChannel.register(this.selector, SelectionKey.OP_READ);

                    clientMap.put(readKey, new ClientHandler(readKey, acceptedChannel));

                    System.out.println("New client ip=" + acceptedChannel.getRemoteAddress() + ", total clients=" + clientMap.size());
                } else if (key.isReadable()) {
                    ClientHandler ch = clientMap.get(key);
                    if (ch == null)
                        continue;
                    this.clientThreadExecutor.execute(ch);
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        this.selector.selectedKeys().clear();
    }

    public static void main(String[] args) {
        new Server(null, 8080);
    }
}
