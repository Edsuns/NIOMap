import nio.AESEncoder;
import nio.NIOComponent;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public class NIOMapClient extends NIOComponent<Queue<NIOMapClient.Command>> {

    static class Command {

        final String message;
        volatile String returnVal;

        private Command(String... cmd) {
            this.message = String.join(" ", cmd);
        }

        void onReturn(String message) {
            synchronized (Command.this) {
                returnVal = message;
                Command.this.notifyAll();
            }
        }

        public Future<String> returnValFuture() {
            return (ReturnValueFuture) () -> {
                String val = returnVal;
                if (val == null) {
                    synchronized (Command.this) {
                        val = returnVal;
                        if (val != null) return val;
                        Command.this.wait(TIMEOUT_MS);
                        val = returnVal;
                        if (val == null) {
                            throw new TimeoutException();
                        }
                    }
                }
                return "null".equals(val) ? null : val;
            };
        }
    }

    private final Queue<Command> commandQueue = new ConcurrentLinkedQueue<>();

    private final AtomicInteger cmdNeedReturn = new AtomicInteger(0);

    protected NIOMapClient(SocketAddress address, AESEncoder encoder) {
        super(address, false, encoder, LinkedList::new);
    }

    @Override
    protected void onMessage(ChannelContext<Queue<Command>> context, String message) {
        Command command = Objects.requireNonNull(context.attachment.poll());
        command.onReturn(message);

        if (cmdNeedReturn.decrementAndGet() <= 0 && commandQueue.isEmpty()) {
            synchronized (cmdNeedReturn) {
                cmdNeedReturn.notifyAll();
            }
        }
    }

    @Override
    protected void onWritable(ChannelContext<Queue<Command>> context) throws IOException {
        Command command;
        while ((command = commandQueue.poll()) != null) {
            write(context, command.message);
            context.attachment.add(command);

            cmdNeedReturn.incrementAndGet();
        }
    }

    public void awaitFlush(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException {
        if (cmdNeedReturn.decrementAndGet() <= 0 && commandQueue.isEmpty()) return;
        long limitMs = unit.toMillis(timeout);
        synchronized (cmdNeedReturn) {
            cmdNeedReturn.wait(limitMs);
        }
    }

    private Future<String> enqueueCommand(String... cmd) {
        Command command = new Command(cmd);
        commandQueue.add(command);
        return command.returnValFuture();
    }

    /**
     * @see Map#put(Object, Object)
     */
    public Future<String> put(String key, String val) {
        return enqueueCommand("put", key, val);
    }

    /**
     * @see Map#get(Object)
     */
    public Future<String> get(String key) {
        return enqueueCommand("get", key);
    }

    /**
     * @see Map#remove(Object)
     */
    public Future<String> remove(String key) {
        return enqueueCommand("rm", key);
    }

    /**
     * @see Map#size()
     */
    public Future<String> size() {
        return enqueueCommand("size");
    }

    /**
     * @see Map#clear()
     */
    public Future<String> clear() {
        return enqueueCommand("clear");
    }
}
