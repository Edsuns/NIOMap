import nio.AESEncoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public class NIOMapTest {

    @Test
    public void basic() throws IOException, ExecutionException, InterruptedException, NoSuchAlgorithmException {
        AESEncoder encoder = AESEncoder.generateEncoder();
        InetSocketAddress address = new InetSocketAddress(6636);
        NIOMapServer nioMapServer = new NIOMapServer(address, encoder);
        NIOMapClient nioMapClient = new NIOMapClient(address, encoder);
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
    public void concurrent() throws IOException, InterruptedException,
            TimeoutException, ExecutionException, NoSuchAlgorithmException {
        AESEncoder encoder = AESEncoder.generateEncoder();
        InetSocketAddress address = new InetSocketAddress(6639);
        NIOMapServer nioMapServer = new NIOMapServer(address, encoder);
        nioMapServer.connect();

        final int x = 3, y = 4, threads = x * y;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        List<NIOMapClient> clients = new ArrayList<>(x);
        List<Future<Void>> futures = new LinkedList<>();
        for (int i = 0; i < x; i++) {
            NIOMapClient nioMapClient = new NIOMapClient(address, encoder);
            nioMapClient.connect();
            clients.add(nioMapClient);
        }
        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                futures.add(executorService.submit(new QueryTask(i, clients.get(i))));
            }
        }

        DecimalFormat df = new DecimalFormat(",###0.0000");
        Function<Long, String> calcQPS = start ->
                df.format(180_000d / ((System.currentTimeMillis() - start) / 1000d)) + " QPS";
        try {
            long start = System.currentTimeMillis();
            for (Future<Void> future : futures) {
                future.get(10_000L, TimeUnit.MILLISECONDS);
            }
            System.out.println("enqueue: " + calcQPS.apply(start));
            for (NIOMapClient client : clients) {
                client.awaitFlush(10_000L, TimeUnit.MILLISECONDS);
            }
            System.out.println("flush: " + calcQPS.apply(start));
            System.out.println("total: " + (System.currentTimeMillis() - start) + "ms");
            assertEquals(x * 5000, nioMapServer.map.size());
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
