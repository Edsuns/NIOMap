import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by Edsuns@qq.com on 2022/4/14.
 */
public class MessageEscapeTest {

    @Test
    public void test() {
        String s = "abc\\mn\\\\\\n\ndef\\\n";
        byte[] escaped = InputOutput.escape(s.getBytes(StandardCharsets.UTF_8));
        assertEquals(s, new String(InputOutput.unescape(escaped), StandardCharsets.UTF_8));
    }
}
