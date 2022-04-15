import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public abstract class NIOComponent<AT> implements Closeable {

    protected static class ChannelContext<T> {
        protected final SocketChannel channel;
        protected final T attachment;
        final ByteBuffer inputBuffer = ByteBuffer.allocate(4096);
        final Queue<ByteBuffer> outputQueue = new LinkedList<>();
        AESEncoder encoder;
        int state = CREATE;

        @SuppressWarnings("unchecked")
        ChannelContext(SocketChannel channel, Object attachment) {
            this.channel = channel;
            this.attachment = (T) attachment;
        }
    }

    static final int CREATE = 0, CLIENT_OK = 1, SERVER_OK = 2, CONNECTED = 3;
    static final String OK = "OK";

    static final byte MESSAGE_DELIMITER = '\n';

    /**
     * {@link SelectionKey}
     */
    static final int OPS = SelectionKey.OP_READ | SelectionKey.OP_WRITE;

    protected final boolean isServer;
    protected final SocketAddress address;
    protected final AESEncoder encoder;
    protected final Supplier<AT> attachmentSupplier;

    volatile Selector selector;
    Thread thread;

    protected NIOComponent(SocketAddress address, boolean isServer, AESEncoder encoder, Supplier<AT> attachmentSupplier) {
        this.isServer = isServer;
        this.address = address;
        this.encoder = encoder;
        this.attachmentSupplier = attachmentSupplier;
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

    @SuppressWarnings("unchecked")
    private ChannelContext<AT> context(SelectionKey key) {
        return (ChannelContext<AT>) key.attachment();
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
        Selector s;
        while (!Thread.currentThread().isInterrupted() && (s = selector) != null) {
            SelectionKey key = null;
            try {
                if (s.select(1000L) <= 0) {
                    continue;
                }
                Iterator<SelectionKey> iterator = s.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isConnectable()) {
                        onConnectable(key);
                    } else if (key.isAcceptable()) {
                        onAcceptable(key);
                    }
                    if (key.isReadable()) {
                        ChannelContext<AT> context = context(key);
                        if (context.state != CONNECTED) {
                            handleConnectionOnReadable(context);
                            continue;
                        }
                        onReadable(context);
                    }
                    if (key.isWritable()) {
                        ChannelContext<AT> context = context(key);
                        if (context.state != CONNECTED) {
                            handleConnectionOnWritable(context);
                            continue;
                        }
                        onWritable(context);
                    }
                }
            } catch (ClosedSelectorException | CancelledKeyException e) {
                return;
            } catch (Exception e) {
                // TODO
                e.printStackTrace();
                try {
                    if (key != null) {
                        key.cancel();
                        key.channel().close();
                    }
                    if (!isServer) {
                        close();
                        return;
                    }
                } catch (IOException ex) {
                    // TODO
                    ex.printStackTrace();
                }
            }
        }
    }

    private void handleConnectionOnReadable(ChannelContext<AT> context) throws IOException {
        ByteBuffer buffer = context.inputBuffer;
        byte[] bf = buffer.array();

        /* client CLIENT_OK -> CONNECTED */
        if (!isServer) {
            if (context.state != CLIENT_OK) {
                return;
            }
            if (context.channel.read(buffer) <= 0) {
                return;
            }
            int p = 0;
            while (p < buffer.position() && bf[++p] != MESSAGE_DELIMITER) {
            }
            if (p == 0 || p >= buffer.position()) {
                return;
            }
            byte[] bytes = decode(context.encoder, buffer, 0, p);
            String msg = new String(bytes, StandardCharsets.UTF_8);
            if (!OK.equals(msg)) {
                throw new ConnectException("Failed to establish secure connection!");
            }
            strip(buffer, p + 1, buffer.position() - p - 1);
            context.state = CONNECTED;
            return;
        }

        /* server CREATE -> SERVER_OK */
        if (context.state != CREATE) {
            return;
        }
        if (context.channel.read(buffer) <= 0) {
            return;
        }
        int p = 0;
        while (p < buffer.position() && bf[++p] != MESSAGE_DELIMITER) {
        }
        int q = p;
        while (q < buffer.position() && bf[++q] != MESSAGE_DELIMITER) {
        }
        if (p == 0 || q == p || q >= buffer.position()) {
            return;
        }
        byte[] secretKey = decode(encoder, buffer, 0, p);
        byte[] iv = decode(encoder, buffer, p + 1, q - p - 1);
        context.encoder = new AESEncoder(secretKey, iv);
        strip(buffer, q + 1, buffer.position() - q - 1);
        context.state = SERVER_OK;
    }

    static void strip(ByteBuffer buffer, int pos, int count) {
        System.arraycopy(buffer.array(), pos, buffer.array(), 0, count);
        buffer.position(count);
    }

    static byte[] copyOf(byte[] src, int srcPos, int newLength) {
        byte[] copy = new byte[newLength];
        System.arraycopy(src, srcPos, copy, 0, Math.min(src.length, newLength));
        return copy;
    }

    private void handleConnectionOnWritable(ChannelContext<AT> context)
            throws IOException, NoSuchAlgorithmException {
        /* server SERVER_OK -> CONNECTED */
        if (isServer) {
            if (context.state == SERVER_OK) {
                write(context, OK);
                context.state = CONNECTED;
            }
            return;
        }

        /* client CREATE -> CLIENT_OK */
        if (context.state == CREATE) {
            context.encoder = AESEncoder.generateEncoder();
            write(context, encoder, context.encoder.secretKey.getEncoded());
            write(context, encoder, context.encoder.iv.getIV());
            context.state = CLIENT_OK;
        }
    }

    private byte[] decode(AESEncoder encoder, ByteBuffer buffer, int pos, int count) throws IOException {
        try {
            return encoder.decrypt(unescape(copyOf(buffer.array(), pos, count)));
        } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
            throw new IOException(e);
        }
    }

    protected void write(ChannelContext<AT> context, String msg) throws IOException {
        write(context, context.encoder, msg.getBytes(StandardCharsets.UTF_8));
    }

    private void write(ChannelContext<AT> context,
                       AESEncoder encoder, byte[] bytes) throws IOException {
        try {
            bytes = escape(encoder.encrypt(bytes));
        } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
            throw new IOException(e);
        }
        byte[] msg = copyOf(bytes, 0, bytes.length + 1);
        msg[msg.length - 1] = MESSAGE_DELIMITER;
        context.outputQueue.add(ByteBuffer.wrap(msg));

        ByteBuffer bf = context.outputQueue.peek();
        while (bf != null) {
            if (context.channel.write(bf) <= 0) {
                break;
            }
            if (!bf.hasRemaining()) {
                context.outputQueue.poll();
                bf = context.outputQueue.peek();
            }
        }
        if (bf != null && !bf.hasRemaining()) {
            context.outputQueue.poll();
        }
    }

    protected void onConnectable(SelectionKey key) throws IOException {
        SocketChannel serverChannel = (SocketChannel) key.channel();
        serverChannel.finishConnect();
        serverChannel.register(selector, OPS);
        key.attach(new ChannelContext<>(serverChannel, attachmentSupplier.get()));
    }

    protected void onAcceptable(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        SelectionKey clientKey = clientChannel.register(selector, OPS);
        clientKey.attach(new ChannelContext<>(clientChannel, attachmentSupplier.get()));
    }

    protected void onReadable(ChannelContext<AT> context) throws IOException {
        ByteBuffer buffer = context.inputBuffer;
        if (context.channel.read(buffer) <= 0) {
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
                byte[] msg = decode(context.encoder, buffer, idx + 1, len);
                onMessage(context, new String(msg, StandardCharsets.UTF_8));
                idx = i;
            }
        }
        if (idx >= 0) {
            strip(buffer, idx + 1, buffer.position() - idx - 1);
        }
    }

    static byte[] escape(byte[] src) {
        int c = 0;
        for (byte b : src) {
            if (b == '\\' || b == MESSAGE_DELIMITER) {
                c++;
            }
        }
        if (c == 0) {
            return src;
        }
        byte[] result = new byte[src.length + c];
        int p = 0;
        for (byte b : src) {
            if (b == '\\') {
                result[p++] = '\\';
                result[p++] = '\\';
            } else if (b == MESSAGE_DELIMITER) {
                result[p++] = '\\';
                result[p++] = 'n';
            } else {
                result[p++] = b;
            }
        }
        return result;
    }

    static byte[] unescape(byte[] src) {
        byte[] bf = new byte[src.length];
        int p = 0, slash = 0;
        for (byte b : src) {
            if (b == '\\') {
                slash++;
                if (slash == 2) {
                    bf[p++] = '\\';
                    slash = 0;
                }
                continue;
            }
            if (b == 'n' && slash == 1) {
                bf[p++] = MESSAGE_DELIMITER;
                slash = 0;
                continue;
            }
            bf[p++] = b;
        }
        return Arrays.copyOf(bf, p);
    }

    protected abstract void onMessage(ChannelContext<AT> context, String message);

    protected abstract void onWritable(ChannelContext<AT> context) throws IOException;
}
