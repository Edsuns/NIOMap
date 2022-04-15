import java.net.SocketAddress;
import java.util.*;

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
    protected void onMessage(Queue<String> attachment, String message) {
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

        attachment.add(returnVal != null ? returnVal : "null");
    }

    @Override
    protected List<String> onWritable(Queue<String> attachment) {
        List<String> messages = new ArrayList<>(attachment.size());
        String returnVal;
        while ((returnVal = attachment.poll()) != null) {
            messages.add(returnVal);
        }
        return messages;
    }
}
