import java.util.concurrent.*;

/**
 * Created by Edsuns@qq.com on 2022/4/12.
 */
interface ReturnValueFuture extends Future<String>, Callable<String> {

    @Override
    default boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean isDone() {
        throw new UnsupportedOperationException();
    }

    @Override
    default String get() throws InterruptedException, ExecutionException {
        try {
            return get(-1L, null);
        } catch (TimeoutException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    default String get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return call();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            }
            if (e instanceof ExecutionException) {
                throw (ExecutionException) e;
            }
            if (e instanceof TimeoutException) {
                throw (TimeoutException) e;
            }
            throw new ExecutionException(e);
        }
    }
}
