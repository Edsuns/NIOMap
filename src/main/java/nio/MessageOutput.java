package nio;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Edsuns@qq.com on 2022/4/15.
 */
class MessageOutput implements InputOutput {
    final NIOComponent.ChannelContext<?> context;
    final Queue<ByteBuffer> queue = new LinkedList<>();

    MessageOutput(NIOComponent.ChannelContext<?> context) {
        this.context = context;
    }

    void write(byte[] bytes) throws IOException {
        write(context.encoder, bytes);
    }

    void write(AESEncoder encoder, byte[] bytes) throws IOException {
        try {
            bytes = InputOutput.escape(encoder.encrypt(bytes));
        } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
            throw new IOException(e);
        }
        byte[] msg = InputOutput.copyOf(bytes, 0, bytes.length + 1);
        msg[msg.length - 1] = MESSAGE_DELIMITER;
        queue.add(ByteBuffer.wrap(msg));

        ByteBuffer bf = queue.peek();
        while (bf != null) {
            if (context.channel.write(bf) <= 0) {
                break;
            }
            if (!bf.hasRemaining()) {
                queue.poll();
                bf = queue.peek();
            }
        }
        if (bf != null && !bf.hasRemaining()) {
            queue.poll();
        }
    }
}
