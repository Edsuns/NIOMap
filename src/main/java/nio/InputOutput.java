package nio;

import java.util.Arrays;

/**
 * Created by Edsuns@qq.com on 2022/4/15.
 */
interface InputOutput {

    int BUFFER_SIZE = 4096;

    byte MESSAGE_DELIMITER = '\n';

    static byte[] escape(byte[] src) {
        int c = 0;
        for (byte b : src) {
            if (b == '\\' || b == MESSAGE_DELIMITER) {
                c++;
            }
        }
        if (c == 0) {
            return src;
        }
        byte[] result = new byte[src.length + c];
        int p = 0;
        for (byte b : src) {
            if (b == '\\') {
                result[p++] = '\\';
                result[p++] = '\\';
            } else if (b == MESSAGE_DELIMITER) {
                result[p++] = '\\';
                result[p++] = 'n';
            } else {
                result[p++] = b;
            }
        }
        return result;
    }

    static byte[] unescape(byte[] src) {
        byte[] bf = new byte[src.length];
        int p = 0, slash = 0;
        for (byte b : src) {
            if (b == '\\') {
                slash++;
                if (slash == 2) {
                    bf[p++] = '\\';
                    slash = 0;
                }
                continue;
            }
            if (b == 'n' && slash == 1) {
                bf[p++] = MESSAGE_DELIMITER;
                slash = 0;
                continue;
            }
            bf[p++] = b;
        }
        return Arrays.copyOf(bf, p);
    }

    static byte[] copyOf(byte[] src, int srcPos, int newLength) {
        byte[] copy = new byte[newLength];
        System.arraycopy(src, srcPos, copy, 0, Math.min(src.length, newLength));
        return copy;
    }
}
