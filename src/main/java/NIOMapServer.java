import nio.AESEncoder;
import nio.NIOComponent;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public class NIOMapServer extends NIOComponent<Queue<String>> {

    final Map<String, String> map;

    protected NIOMapServer(SocketAddress address, AESEncoder encoder) {
        this(address, encoder, new HashMap<>());
    }

    protected NIOMapServer(SocketAddress address, AESEncoder encoder, Map<String, String> map) {
        super(address, true, encoder, LinkedList::new);
        this.map = map;
    }

    @Override
    protected void onMessage(ChannelContext<Queue<String>> context, String message) {
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

        context.attachment.add(returnVal != null ? returnVal : "null");
    }

    @Override
    protected void onWritable(ChannelContext<Queue<String>> context) throws IOException {
        String returnVal;
        while ((returnVal = context.attachment.poll()) != null) {
            write(context, returnVal);
        }
    }
}
