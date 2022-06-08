package com.hazelcast.simulator.tests.wan;

import com.hazelcast.client.impl.clientside.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.MCChangeWanReplicationStateCodec;
import com.hazelcast.client.impl.protocol.codec.MCWanSyncMapCodec;
import com.hazelcast.client.impl.spi.impl.ClientInvocation;
import com.hazelcast.client.impl.spi.impl.ClientInvocationFuture;
import com.hazelcast.cluster.Member;
import com.hazelcast.map.IMap;
import com.hazelcast.simulator.hz.HazelcastTest;
import com.hazelcast.simulator.test.StopException;
import com.hazelcast.simulator.test.annotations.Prepare;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.test.annotations.TimeStep;
import com.hazelcast.simulator.tests.helpers.KeyLocality;
import com.hazelcast.simulator.worker.loadsupport.Streamer;
import com.hazelcast.simulator.worker.loadsupport.StreamerFactory;

import java.util.Random;
import java.util.concurrent.ExecutionException;

import static com.hazelcast.simulator.tests.helpers.KeyUtils.generateIntKeys;
import static com.hazelcast.simulator.utils.GeneratorUtils.generateByteArray;

public class UniDirectionalLondon extends HazelcastTest {

    // wan specific
    public int sleepMillis = 1_000;
    public String wanReplicationName = "to-tokyo-replication";
    public String wanPublisherId = "to-tokyo-publisher-id";
    public String syncMapName = "from-london-sync-map";
    public String replyMapName = "from-tokyo-reply-map";


    // IntByteMapTest
    public int keyCount = 1_000_000;
    public int valueCount = 1_000;
    public int minSize = 16;
    public int maxSize = 2_000;
    public KeyLocality keyLocality = KeyLocality.SHARED;

    // private
    private IMap<Integer, Object> syncMap;
    private IMap<Integer, Integer> replyMap;
    private int[] keys;

    @Setup
    public void setUp() {
        syncMap = targetInstance.getMap(syncMapName);
        replyMap = targetInstance.getMap(replyMapName);
        keys = generateIntKeys(keyCount, keyLocality, targetInstance);

        if (minSize > maxSize) {
            throw new IllegalStateException("minSize can't be larger than maxSize");
        }
    }

    @Prepare(global = true)
    public void prepare() throws ExecutionException, InterruptedException {
        pauseReplication();

        populateSyncedMapV2();

        startWanSync();
    }

    @TimeStep(executionGroup = "sleep")
    public void waitForSyncCompletion() throws InterruptedException {
        if (replyMap.size() == 0) {
            Thread.sleep(sleepMillis);
        } else {
            throw new StopException();
        }
    }

    @Teardown(global = true)
    public void tearDown() {
        syncMap.destroy();
    }

    private void pauseReplication() throws ExecutionException, InterruptedException {
        HazelcastClientInstanceImpl client = ((HazelcastClientProxy) targetInstance).client;
        ClientMessage request = MCChangeWanReplicationStateCodec.encodeRequest(wanReplicationName, wanPublisherId, (byte) 1);
        for (Member m : client.getClientClusterService().getMemberList()) {
            logger.info("++ " + m.getUuid());
            ClientInvocationFuture response = new ClientInvocation(client, request, "some-name", m.getUuid()).invoke();
            response.get();
        }
        Thread.sleep(sleepMillis);
    }

    private void startWanSync() throws ExecutionException, InterruptedException {
        HazelcastClientInstanceImpl client = ((HazelcastClientProxy) targetInstance).client;
        ClientMessage request = MCWanSyncMapCodec.encodeRequest(wanReplicationName, wanPublisherId, 1, syncMapName);
        ClientInvocationFuture response = new ClientInvocation(client, request, "some-name", -1).invoke();
        response.get();
    }

    private byte[][] generateValues(Random random) {
        byte[][] values = new byte[valueCount][];
        for (int i = 0; i < values.length; i++) {
            int delta = maxSize - minSize;
            int length = delta == 0 ? minSize : minSize + random.nextInt(delta);
            values[i] = generateByteArray(random, length);
        }
        return values;
    }

    private void populateSyncedMap() {
        Random random = new Random();

        byte[][] values = generateValues(random);

        Streamer<Integer, Object> streamer = StreamerFactory.getInstance(syncMap);
        for (int key : keys) {
            streamer.pushEntry(key, values[random.nextInt(values.length)]);
        }
        streamer.await();
    }

    private void populateSyncedMapV2() throws InterruptedException {
        Random random = new Random();

        byte[][] values = generateValues(random);
        for (int key : keys) {
            syncMap.putAsync(key, values[random.nextInt(values.length)]);
        }

        int size;
        while ((size = syncMap.size()) < keyCount) {
            Thread.sleep(sleepMillis);
            logger.info("-- " + size);
        }
    }
}
