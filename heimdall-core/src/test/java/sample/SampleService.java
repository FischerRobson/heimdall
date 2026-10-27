package sample;

import jakarta.enterprise.context.ApplicationScoped;
import com.fischer.heimdall.tracing.HeimdallMonitor;

/**
 * Stand-in for a host-application service. Lives outside Heimdall's own packages
 * because the interceptor deliberately skips classes in "com.fischer.heimdall.".
 */
@ApplicationScoped
@HeimdallMonitor
public class SampleService {

    public String greet(String name, String password) {
        return "hello " + name;
    }

    public void fail() {
        throw new IllegalStateException("boom");
    }

    public void failWithError() {
        throw new AssertionError();
    }
}
