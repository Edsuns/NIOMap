package nio;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Edsuns@qq.com on 2022/4/15.
 */
class MessageInput implements InputOutput {
    final NIOComponent.ChannelContext<?> context;
    ByteBuffer bf = ByteBuffer.allocate(BUFFER_SIZE);
    final Deque<Integer> split = new LinkedList<>();

    static final AtomicLong read = new AtomicLong(0L);
    static final AtomicLong decode = new AtomicLong(0L);

    MessageInput(NIOComponent.ChannelContext<?> context) {
        this.context = context;
    }

    boolean read() throws IOException {
        return read(-1);
    }

    boolean read(int required) throws IOException {
        long start = System.currentTimeMillis();
        do {
            long finalStart = start;
            read.getAndUpdate(old -> old + (System.currentTimeMillis() - finalStart));
            if (!bf.hasRemaining()) {
                int p = bf.position();
                bf = ByteBuffer.wrap(InputOutput.copyOf(bf.array(), 0, p + BUFFER_SIZE));
                bf.position(p);
            }
            start = System.currentTimeMillis();
        } while (context.channel.read(bf) > 0);
        long finalStart1 = start;
        read.getAndUpdate(old -> old + (System.currentTimeMillis() - finalStart1));
        Integer last = split.peekLast();
        for (int i = last != null ? last + 1 : 0; i < bf.position(); i++) {
            if (bf.get(i) == MESSAGE_DELIMITER) {
                split.add(i);
            }
        }
        return split.size() >= required;
    }

    List<byte[]> strip() throws IOException {
        return strip(Integer.MAX_VALUE);
    }

    List<byte[]> strip(int maxCount) throws IOException {
        return strip(context.encoder, maxCount);
    }

    List<byte[]> strip(AESEncoder encoder, int maxCount) throws IOException {
        long start = System.currentTimeMillis();
        List<byte[]> result = new ArrayList<>();
        int left = -1;
        for (int i = 0, c = Math.min(maxCount, split.size()); i < c; i++) {
            int right = split.pop();
            byte[] bytes = InputOutput.copyOf(bf.array(), left + 1, right - left - 1);
            result.add(decode(encoder, bytes));
            left = right;
        }
        strip(bf, left + 1, bf.position() - left - 1);
        decode.getAndUpdate(old -> old + (System.currentTimeMillis() - start));
        return result;
    }

    private byte[] decode(AESEncoder encoder, byte[] bytes) throws IOException {
        try {
            return encoder.decrypt(InputOutput.unescape(bytes));
        } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
            throw new IOException(e);
        }
    }

    static void strip(ByteBuffer buffer, int pos, int count) {
        System.arraycopy(buffer.array(), pos, buffer.array(), 0, count);
        buffer.position(count);
    }
}
