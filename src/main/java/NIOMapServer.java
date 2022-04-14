import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public class NIOMapServer extends NIOComponent<NIOMapServer.ServerAttachment> {

    static class ServerAttachment {
        final Queue<String> queue = new LinkedList<>();
        final ByteBuffer buffer = ByteBuffer.allocate(2048);
    }

    final Map<String, String> map;

    protected NIOMapServer(SocketAddress address) {
        this(address, new HashMap<>());
    }

    protected NIOMapServer(SocketAddress address, Map<String, String> map) {
        super(address, true, ServerAttachment::new);
        this.map = map;
    }

    @Override
    protected ByteBuffer getBuffer(ServerAttachment attachment) {
        return attachment.buffer;
    }

    @Override
    protected void onMessage(ServerAttachment attachment, String message) {
        String returnVal;
        String[] cmd = message.split(" ");
        if ("put".equals(cmd[0])) {
            returnVal = map.put(cmd[1], cmd[2]);
        } else if ("get".equals(cmd[0])) {
            returnVal = map.get(cmd[1]);
        } else if ("rm".equals(cmd[0])) {
            returnVal = map.remove(cmd[1]);
        } else if ("size".equals(cmd[0])) {
            returnVal = String.valueOf(map.size());
        } else {
            throw new UnsupportedOperationException(message);
        }

        attachment.queue.add(returnVal != null ? returnVal : "null");
    }

    @Override
    protected List<String> onWritable(ServerAttachment attachment) {
        List<String> messages = new ArrayList<>(attachment.queue.size());
        String returnVal;
        while ((returnVal = attachment.queue.poll()) != null) {
            messages.add(returnVal);
        }
        return messages;
    }
}
