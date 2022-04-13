import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
class ReturnValueFuture implements Future<String> {

    final ReturnValueTask task;

    ReturnValueFuture(ReturnValueTask task) {
        this.task = task;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDone() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String get() throws InterruptedException {
        try {
            return get(10_000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        return task.get(timeout, unit);
    }
}
