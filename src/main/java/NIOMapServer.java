import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public class NIOMapServer extends NIOComponent {

    static class ServerAttachment {
        final Queue<String> queue = new LinkedList<>();
        final ByteBuffer buffer = ByteBuffer.allocate(2048);
    }

    final Map<String, String> map;

    protected NIOMapServer(SocketAddress address) {
        this(address, new HashMap<>());
    }

    protected NIOMapServer(SocketAddress address, Map<String, String> map) {
        super(address, true);
        this.map = map;
    }

    private ServerAttachment attach(SelectionKey key) {
        ServerAttachment attachment = (ServerAttachment) key.attachment();
        if (attachment == null) {
            attachment = new ServerAttachment();
            key.attach(attachment);
        }
        return attachment;
    }

    @Override
    protected ByteBuffer getBuffer(SelectionKey key) {
        return attach(key).buffer;
    }

    @Override
    protected void onMessage(SelectionKey sender, byte[] message) {
        String msg = new String(message, StandardCharsets.UTF_8), returnVal;
        String[] cmd = msg.split(" ");
        if ("put".equals(cmd[0])) {
            returnVal = map.put(cmd[1], cmd[2]);
        } else if ("get".equals(cmd[0])) {
            returnVal = map.get(cmd[1]);
        } else if ("rm".equals(cmd[0])) {
            returnVal = map.remove(cmd[1]);
        } else if ("size".equals(cmd[0])) {
            returnVal = String.valueOf(map.size());
        } else {
            throw new UnsupportedOperationException(msg);
        }

        Queue<String> attachment = attach(sender).queue;
        attachment.add(returnVal != null ? returnVal : "null");
    }

    @Override
    protected void onWritable(SelectionKey key) throws IOException {
        Queue<String> attachment = attach(key).queue;
        SocketChannel channel = (SocketChannel) key.channel();
        String returnVal;
        while ((returnVal = attachment.poll()) != null) {
            String msg = returnVal + MESSAGE_DELIMITER;
            ByteBuffer bf = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            while (bf.hasRemaining()) {
                channel.write(bf);
            }
        }
    }
}
