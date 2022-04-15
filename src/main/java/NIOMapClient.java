import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public class NIOMapClient extends NIOComponent<Queue<NIOMapClient.Command>> {

    static class Command {

        final String message;
        boolean returned = false;
        String returnVal;

        private Command(String... cmd) {
            this.message = String.join(" ", cmd);
        }

        void onReturn(String message) {
            synchronized (this) {
                returnVal = "null".equals(message) ? null : message;
                returned = true;
                this.notifyAll();
            }
        }

        public Future<String> returnValFuture() {
            return new ReturnValueFuture((timeout, unit) -> {
                boolean r = returned;
                if (r) {
                    return returnVal;
                }
                synchronized (this) {
                    r = returned;
                    if (r) {
                        return returnVal;
                    }
                    this.wait(unit.toMillis(timeout));
                    r = returned;
                    if (!r) {
                        throw new TimeoutException();
                    }
                    return returnVal;
                }
            });
        }
    }

    private final Queue<Command> commandQueue = new ConcurrentLinkedQueue<>();

    volatile Command lastCommand;
    static final AtomicReferenceFieldUpdater<NIOMapClient, Command>
            lastCommandUpdater = AtomicReferenceFieldUpdater.newUpdater(
            NIOMapClient.class, Command.class, "lastCommand"
    );

    protected NIOMapClient(SocketAddress address, AESEncoder encoder) {
        super(address, false, encoder, LinkedList::new);
    }

    @Override
    protected void onMessage(Queue<Command> attachment, String message) {
        Command command = Objects.requireNonNull(attachment.poll());
        command.onReturn(message);
    }

    @Override
    protected List<String> onWritable(Queue<Command> attachment) {
        List<String> messages = new LinkedList<>();
        Command command;
        while ((command = commandQueue.poll()) != null) {
            messages.add(command.message);
            attachment.add(command);
            lastCommandUpdater.set(this, command);
        }
        return messages;
    }

    public void awaitFlush(long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        long limitMs = unit.toMillis(timeout);
        long start = System.currentTimeMillis();
        while (!commandQueue.isEmpty()) {
            if (System.currentTimeMillis() - start > limitMs) {
                throw new TimeoutException();
            }
        }
        Command command = lastCommandUpdater.get(this);
        if (command != null) {
            limitMs -= (System.currentTimeMillis() - start);
            command.returnValFuture().get(limitMs, TimeUnit.MILLISECONDS);
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
}
