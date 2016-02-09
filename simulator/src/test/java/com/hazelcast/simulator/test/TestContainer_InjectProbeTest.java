package com.hazelcast.simulator.test;

import com.hazelcast.simulator.probes.Probe;
import com.hazelcast.simulator.test.annotations.InjectProbe;
import com.hazelcast.simulator.test.annotations.InjectTestContext;
import com.hazelcast.simulator.test.annotations.Run;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestContainer_InjectProbeTest extends AbstractTestContainerTest {

    @Test
    public void testInjectProbe() throws Exception {
        ProbeTest test = new ProbeTest();
        testContainer = createTestContainer(test);

        assertNotNull(test.probe);
        assertFalse(test.probe.isThroughputProbe());
        assertTrue(testContainer.hasProbe("probe"));

        testContainer.invoke(TestPhase.RUN);
        Map<String, Probe> probeMap = testContainer.getProbeMap();
        assertTrue(probeMap.size() > 0);
        assertTrue(probeMap.keySet().contains("probe"));
    }

    @Test
    public void testInjectProbe_withName() {
        ProbeTest test = new ProbeTest();
        testContainer = createTestContainer(test);

        assertNotNull(test.namedProbe);
        assertTrue(testContainer.hasProbe("explicitProbeName"));
    }

    @Test
    public void testInjectProbe_withUseForThroughput() {
        ProbeTest test = new ProbeTest();
        testContainer = createTestContainer(test);

        assertNotNull(test.throughputProbe);
        assertTrue(test.throughputProbe.isThroughputProbe());
        assertTrue(testContainer.hasProbe("throughputProbe"));
    }

    private static class ProbeTest extends BaseTest {

        @InjectTestContext
        private TestContext context;

        @InjectProbe
        private Probe probe;

        @InjectProbe(name = "explicitProbeName")
        private Probe namedProbe;

        @InjectProbe(useForThroughput = true)
        private Probe throughputProbe;

        @Run
        public void run() {
            probe.started();
            probe.done();
        }
    }
}
