import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Edsuns@qq.com on 2022/4/13.
 */
public class MapBenchmark {

    public static void main(String[] args) {
        long hashMap = 0, concurrentHashMap = 0;
        hashMap += benchmark("HashMap", new HashMap<>());
        concurrentHashMap += benchmark("ConcurrentHashMap", new ConcurrentHashMap<>());
        hashMap += benchmark("HashMap", new HashMap<>());
        concurrentHashMap += benchmark("ConcurrentHashMap", new ConcurrentHashMap<>());
        hashMap += benchmark("HashMap", new HashMap<>());
        concurrentHashMap += benchmark("ConcurrentHashMap", new ConcurrentHashMap<>());
        System.out.print("total 9000w | ");
        System.out.print("HashMap: " + hashMap + "ms | ");
        System.out.print("ConcurrentHashMap: " + concurrentHashMap + "ms");
    }

    static long benchmark(String name, Map<String, String> map) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100_000; i++) {
            map.put("k-" + i, "v-" + 1);
        }
        for (int i = 0; i < 200; i++) {
            for (int j = 0; j < 150_000; j++) {
                map.get("k-" + j);
            }
        }
        long duration = System.currentTimeMillis() - start;
        System.out.println(name + " | " + 3000 + "w | " + duration + "ms");
        return duration;
    }
}
