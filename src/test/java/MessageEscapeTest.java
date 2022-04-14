import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by Edsuns@qq.com on 2022/4/14.
 */
public class MessageEscapeTest {

    @Test
    public void test() {
        String s = "abc\\mn\\\\\\n\ndef\\\n";
        String escaped = NIOComponent.escape(s);
        assertEquals(s, NIOComponent.unescape(escaped));
    }
}
