import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
@FunctionalInterface
interface ReturnValueTask {
    String get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException;
}
