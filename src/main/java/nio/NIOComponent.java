package nio;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public abstract class NIOComponent<AT> implements Closeable {

    protected static class ChannelContext<T> {
        public final SocketChannel channel;
        public final T attachment;
        final MessageInput messageInput = new MessageInput(this);
        final MessageOutput messageOutput = new MessageOutput(this);
        AESEncoder encoder;
        int state = CREATE;

        @SuppressWarnings("unchecked")
        ChannelContext(SocketChannel channel, Object attachment) {
            this.channel = channel;
            this.attachment = (T) attachment;
        }
    }

    public static final long TIMEOUT_MS = 10_000;
    static final int CREATE = 0, CLIENT_OK = 1, SERVER_OK = 2, CONNECTED = 3;
    static final String OK = "OK";

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

    protected NIOComponent(SocketAddress address, boolean isServer,
                           AESEncoder encoder, Supplier<AT> attachmentSupplier) {
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
                if (s.select(TIMEOUT_MS) <= 0) {
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
        MessageInput input = context.messageInput;
        /* client CLIENT_OK -> CONNECTED */
        if (!isServer) {
            if (context.state != CLIENT_OK) {
                return;
            }
            if (!input.read(1)) {
                return;
            }
            byte[] bytes = input.strip(1).get(0);
            String msg = new String(bytes, StandardCharsets.UTF_8);
            if (!OK.equals(msg)) {
                throw new ConnectException("Failed to establish secure connection!");
            }
            context.state = CONNECTED;
            return;
        }

        /* server CREATE -> SERVER_OK */
        if (context.state != CREATE) {
            return;
        }
        if (!input.read(2)) {
            return;
        }
        List<byte[]> msg = input.strip(encoder, 2);
        context.encoder = new AESEncoder(msg.get(0), msg.get(1));
        context.state = SERVER_OK;
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
            context.messageOutput.write(encoder, context.encoder.secretKey.getEncoded());
            context.messageOutput.write(encoder, context.encoder.iv.getIV());
            context.state = CLIENT_OK;
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
        if (!context.messageInput.read()) {
            return;
        }
        List<byte[]> messages = context.messageInput.strip();
        for (byte[] msg : messages) {
            onMessage(context, new String(msg, StandardCharsets.UTF_8));
        }
    }

    protected void write(ChannelContext<AT> context, String msg) throws IOException {
        context.messageOutput.write(msg.getBytes(StandardCharsets.UTF_8));
    }

    protected abstract void onMessage(ChannelContext<AT> context, String message);

    protected abstract void onWritable(ChannelContext<AT> context) throws IOException;
}
