package metrics;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeapDumpServiceTest {

    @Test
    void writesHprofFile() throws Exception {
        Path file = new HeapDumpService().dump(true);
        try {
            assertTrue(Files.exists(file));
            assertTrue(file.getFileName().toString().endsWith(".hprof"));
            assertTrue(Files.size(file) > 0);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
