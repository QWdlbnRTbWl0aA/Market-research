package local.myhome.marketresearch.tools;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UnicodeTextTest {
    @Test void boundedPrefixesPreserveWholeSupplementaryCharacters() {
        String emoji="\uD83C\uDF83";
        String text="A".repeat(159)+emoji+"tail";
        assertEquals("A".repeat(159),UnicodeText.prefix(text,160));
        assertEquals("A".repeat(159)+emoji,UnicodeText.prefix(text,161));
        assertEquals("",UnicodeText.prefix(emoji,1));
        for(int limit=0;limit<=text.length();limit++) {
            String clipped=UnicodeText.prefix(text,limit);
            assertTrue(clipped.length()<=limit);
            assertTrue(StandardCharsets.UTF_8.newEncoder().canEncode(clipped));
        }
    }
}
