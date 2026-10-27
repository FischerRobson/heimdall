package metrics;

import com.sun.management.HotSpotDiagnosticMXBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes a heap dump (.hprof) to a temporary file. The caller streams it and must
 * delete it afterwards.
 *
 * A heap dump pauses the JVM while it is written and can be as large as the heap
 * itself — only one may run at a time.
 */
@ApplicationScoped
public class HeapDumpService {

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @param live true to dump only reachable objects (triggers a full GC first,
     *             smaller file); false to include unreachable objects too
     * @throws IllegalStateException if another dump is already running
     * @throws UnsupportedOperationException if the JVM has no HotSpot diagnostic bean
     *         (e.g. a GraalVM native image)
     */
    public Path dump(boolean live) throws IOException {
        if (!lock.tryLock()) {
            throw new IllegalStateException("A heap dump is already in progress");
        }
        try {
            HotSpotDiagnosticMXBean bean;
            try {
                bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            } catch (Exception | LinkageError e) {
                bean = null;
            }
            if (bean == null) {
                throw new UnsupportedOperationException("Heap dumps are not supported on this JVM");
            }

            // dumpHeap refuses to overwrite an existing file — reserve a unique name, then free it
            Path file = Files.createTempFile("heimdall-heap-", ".hprof");
            Files.delete(file);

            bean.dumpHeap(file.toString(), live);
            return file;
        } finally {
            lock.unlock();
        }
    }
}
