package sample;

import jakarta.enterprise.context.ApplicationScoped;
import tracing.HeimdallMonitor;

/**
 * Stand-in for a host-application service. Lives outside Heimdall's own packages
 * because the interceptor deliberately skips classes in "tracing.".
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
