import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public class NIOMapTest {

    @Test
    public void basic() throws IOException, ExecutionException, InterruptedException {
        InetSocketAddress address = new InetSocketAddress(6632);
        NIOMapServer nioMapServer = new NIOMapServer(address);
        NIOMapClient nioMapClient = new NIOMapClient(address);
        nioMapServer.connect();
        nioMapClient.connect();

        final String k1 = "k1", v1 = "v1", v1_1 = "v1-1";
        assertEquals("0", nioMapClient.size().get());
        assertNull(nioMapClient.put(k1, v1).get());
        assertEquals("1", nioMapClient.size().get());
        assertEquals(v1, nioMapClient.get(k1).get());
        assertEquals(v1, nioMapClient.put(k1, v1_1).get());
        assertEquals("1", nioMapClient.size().get());
        assertEquals(v1_1, nioMapClient.remove(k1).get());
        assertEquals("0", nioMapClient.size().get());

        String s = "abc\\mn\\\\\\n\ndef\\\n";
        assertNull(nioMapClient.put(s, s).get());
        assertEquals(s, nioMapClient.get(s).get());

        nioMapClient.close();
        nioMapServer.close();
    }

    @Test
    public void concurrent() throws IOException, InterruptedException, TimeoutException, ExecutionException {
        InetSocketAddress address = new InetSocketAddress(6633);
        NIOMapServer nioMapServer = new NIOMapServer(address);
        nioMapServer.connect();

        final int x = 3, y = 5, threads = x * y;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        List<NIOMapClient> clients = new ArrayList<>(x);
        List<Future<Void>> futures = new LinkedList<>();
        for (int i = 0; i < x; i++) {
            NIOMapClient nioMapClient = new NIOMapClient(address);
            nioMapClient.connect();
            clients.add(nioMapClient);
        }
        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                futures.add(executorService.submit(new QueryTask(i, clients.get(i))));
            }
        }

        try {
            long start = System.currentTimeMillis();
            for (Future<Void> future : futures) {
                future.get(10_000L, TimeUnit.MILLISECONDS);
            }
            System.out.println("enqueue: " + (System.currentTimeMillis() - start) + "ms");
            for (NIOMapClient client : clients) {
                client.awaitFlush(10_000L, TimeUnit.MILLISECONDS);
            }
            System.out.println("total 45w queries: " + (System.currentTimeMillis() - start) + "ms");
            assertEquals(x * 10000, nioMapServer.map.size());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AssertionError) {
                throw (AssertionError) e.getCause();
            } else {
                throw e;
            }
        } finally {
            for (NIOMapClient client : clients) {
                client.close();
            }
            nioMapServer.close();
            executorService.shutdownNow();
        }
    }
}
