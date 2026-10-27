package com.fischer.heimdall.connections;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers MBeans with arbitrary attributes on the platform MBeanServer, standing in
 * for the ones Kafka, Hikari, Redis or RabbitMQ clients would publish.
 *
 * The platform server is shared by every test in the JVM, so call {@link #close()}
 * (try-with-resources or @AfterEach) or the fakes leak into other tests.
 */
public class FakeMBeans implements AutoCloseable {

    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    private final List<ObjectName> registered = new ArrayList<>();

    public FakeMBeans register(String objectName, Map<String, Object> attributes) {
        try {
            ObjectName name = new ObjectName(objectName);
            server.registerMBean(new Fake(attributes), name);
            registered.add(name);
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("could not register " + objectName, e);
        }
    }

    @Override
    public void close() {
        for (ObjectName name : registered) {
            try {
                server.unregisterMBean(name);
            } catch (Exception ignored) {}
        }
        registered.clear();
    }

    private record Fake(Map<String, Object> attributes) implements DynamicMBean {

        @Override
        public Object getAttribute(String attribute) throws AttributeNotFoundException {
            if (!attributes.containsKey(attribute)) throw new AttributeNotFoundException(attribute);
            return attributes.get(attribute);
        }

        @Override
        public void setAttribute(Attribute attribute) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AttributeList getAttributes(String[] names) {
            AttributeList list = new AttributeList();
            for (String n : names) {
                if (attributes.containsKey(n)) list.add(new Attribute(n, attributes.get(n)));
            }
            return list;
        }

        @Override
        public AttributeList setAttributes(AttributeList attributes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object invoke(String actionName, Object[] params, String[] signature) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            return new MBeanInfo(Fake.class.getName(), "test fake", null, null, null, null);
        }
    }
}
