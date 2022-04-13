import java.util.concurrent.Callable;

/**
 * Created by Edsuns@qq.com on 2022/4/13.
 */
public class QueryTask implements Callable<Void> {

    final NIOMapClient client;
    final int clientId;

    public QueryTask(int clientId, NIOMapClient client) {
        this.clientId = clientId;
        this.client = client;
    }

    @Override
    public Void call() {
        final int count = 10000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < count / 2; i++) {
            final String k1 = clientId + "k1-" + i, v1 = clientId + "v1-" + i;
            client.put(k1, v1);
        }
        for (int i = 0; i < count; i++) {
            final String k1 = clientId + "k1-" + i;
            client.get(k1);
        }
        for (int i = 0; i < count; i++) {
            final String k1 = clientId + "k1-" + i, v1_1 = clientId + "v1-1-" + i;
            client.put(k1, v1_1);
        }
        System.out.println(clientId + ": " + (System.currentTimeMillis() - start) + "ms");
        return null;
    }
}
