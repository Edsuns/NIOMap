import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public abstract class NIOComponent implements Closeable {

    static final char MESSAGE_DELIMITER = '\n';
    /**
     * {@link SelectionKey}
     */
    static final int OPS = SelectionKey.OP_READ | SelectionKey.OP_WRITE;

    protected final boolean isServer;
    protected final SocketAddress address;

    volatile Selector selector;
    Thread thread;

    protected NIOComponent(SocketAddress address, boolean isServer) {
        this.isServer = isServer;
        this.address = address;
    }

    private AbstractSelectableChannel channel() throws IOException {
        if (isServer) {
            ServerSocketChannel channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.bind(address);
            return channel;
        } else {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(address);
            return channel;
        }
    }

    private Selector selector(AbstractSelectableChannel channel) throws IOException {
        AbstractSelector selector = SelectorProvider.provider().openSelector();
        channel.register(selector, isServer ? SelectionKey.OP_ACCEPT : SelectionKey.OP_CONNECT);
        return selector;
    }

    public synchronized void connect() throws IOException {
        if (selector != null) {
            throw new IllegalStateException("connected");
        }
        this.selector = selector(channel());
        this.thread = new Thread(this::handleEvents);
        this.thread.start();
    }

    @Override
    public synchronized void close() throws IOException {
        Selector s = selector;
        if (s == null) {
            return;
        }
        try {
            s.close();
        } finally {
            thread = null;
            selector = null;
        }
    }

    private void handleEvents() {
        try {
            Selector s;
            while (!Thread.currentThread().isInterrupted() && (s = selector) != null) {
                if (s.select(1000L) <= 0) {
                    continue;
                }
                Iterator<SelectionKey> iterator = s.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isConnectable()) {
                        onConnectable(key);
                    } else if (key.isAcceptable()) {
                        onAcceptable(key);
                    } else if (key.isReadable()) {
                        onReadable(key);
                    } else if (key.isWritable()) {
                        onWritable(key);
                    }
                }
            }
        } catch (ClosedSelectorException ignored) {
        } catch (IOException e) {
            // TODO
            e.printStackTrace();
        } finally {
            try {
                close();
            } catch (IOException e) {
                // TODO
                e.printStackTrace();
            }
        }
    }

    protected void onConnectable(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        socketChannel.finishConnect();
        socketChannel.register(selector, OPS);
    }

    protected void onAcceptable(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, OPS);
    }

    protected void onReadable(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = getBuffer(key);
        if (channel.read(buffer) <= 0) {
            return;
        }
        byte[] bf = buffer.array();
        int idx = -1;
        for (int i = 0; i < buffer.position(); i++) {
            if (bf[i] == MESSAGE_DELIMITER) {
                int len = i - idx - 1;
                if (len <= 0) {
                    continue;
                }
                byte[] msg = new byte[len];
                System.arraycopy(bf, idx + 1, msg, 0, len);
                onMessage(key, msg);
                idx = i;
            }
        }
        if (idx >= 0) {
            int size = buffer.position() - idx - 1;
            System.arraycopy(bf, idx + 1, bf, 0, size);
            buffer.position(size);
        }
    }

    protected abstract ByteBuffer getBuffer(SelectionKey key);

    protected abstract void onMessage(SelectionKey sender, byte[] message);

    protected abstract void onWritable(SelectionKey key) throws IOException;
}
