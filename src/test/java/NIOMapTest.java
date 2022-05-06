import nio.AESEncoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
public class NIOMapTest {

    static final int PORT = 3333;
    static String REMOTE_HOST = "";
    static String REMOTE_ENCODER = "";

    static AESEncoder encoder;
    static InetSocketAddress address;
    static NIOMapServer nioMapServer;

    @BeforeAll
    static void beforeAll() throws NoSuchAlgorithmException, IOException {
        encoder = REMOTE_ENCODER.isEmpty() ? AESEncoder.generateEncoder() : AESEncoder.parse(REMOTE_ENCODER);
        address = new InetSocketAddress(REMOTE_HOST.isEmpty() ? "localhost" : REMOTE_HOST, PORT);
        if (REMOTE_HOST.isEmpty()) {
            nioMapServer = new NIOMapServer(new InetSocketAddress(PORT), encoder);
            nioMapServer.connect();
        }
    }

    @AfterAll
    static void afterAll() throws IOException {
        if (nioMapServer != null) {
            nioMapServer.close();
        }
    }

    @BeforeEach
    void beforeEach() throws IOException, ExecutionException, InterruptedException {
        if (nioMapServer != null) {
            nioMapServer.map.clear();
        } else {
            NIOMapClient nioMapClient = new NIOMapClient(address, encoder);
            nioMapClient.connect();
            nioMapClient.clear().get();
            nioMapClient.close();
        }
    }

    @Test
    public void basic() throws IOException, ExecutionException, InterruptedException {
        NIOMapClient nioMapClient = new NIOMapClient(address, encoder);
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

        // clear
        int size = Integer.parseInt(nioMapClient.size().get());
        assertTrue(size > 0);
        assertEquals(size, Integer.parseInt(nioMapClient.clear().get()));
        assertEquals("0", nioMapClient.size().get());

        nioMapClient.close();
    }

    @Test
    public void concurrent() throws IOException, InterruptedException, TimeoutException, ExecutionException {
        final int x = 2, y = 3, threads = x * y;
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

        DecimalFormat df = new DecimalFormat(",###0.####");
        Function<Long, String> calcQPS = start ->
                df.format(7.5d / ((System.currentTimeMillis() - start) / 1000d)) + "w QPS";
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
            assertEquals(x * 5000, Integer.parseInt(clients.get(0).size().get()));
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
            executorService.shutdownNow();
        }
    }
}
