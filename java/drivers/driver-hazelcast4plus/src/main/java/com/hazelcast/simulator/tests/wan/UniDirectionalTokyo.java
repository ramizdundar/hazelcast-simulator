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
import com.hazelcast.simulator.test.BaseThreadState;
import com.hazelcast.simulator.test.StopException;
import com.hazelcast.simulator.test.annotations.Prepare;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.test.annotations.TimeStep;

import java.util.concurrent.ExecutionException;

public class UniDirectionalTokyo extends HazelcastTest {
    // wan specific
    public int sleepMillis = 100;
    public String wanReplicationName = "to-london-replication";
    public String wanPublisherId = "to-london-publisher-id";
    public String syncMapName = "from-london-sync-map";
    public String replyMapName = "from-tokyo-reply-map";
    public int maxStaleIter = 100;

    // IntByteMapTest
    public int keyCount = 1_000_000;

    // private
    private IMap<Integer, Object> syncMap;
    private IMap<Integer, Integer> replyMap;


    @Setup
    public void setUp() {
        syncMap = targetInstance.getMap(syncMapName);
        replyMap = targetInstance.getMap(replyMapName);
    }

    @Prepare(global = true)
    public void prepare() throws ExecutionException, InterruptedException {
        pauseReplication();
    }

    @TimeStep(executionGroup="sleep")
    public void waitForSyncCompletion(ThreadState state) {
        // some report generation parts will break if this iteration number is too small
        if (state.counter > 1_000_000) throw new StopException();
        else state.counter++;

        long startMillis = -1;
        long finishMillis = -1;
        int lastSize = -1;
        int staleFor = 0;
        while (state.running) {
            int size = syncMap.size();
            if (size > 0) {
                if (startMillis == -1)  startMillis = System.currentTimeMillis();
                if (lastSize == size) staleFor++;
                else {
                    lastSize = size;
                    staleFor = 0;
                }
                if (staleFor == maxStaleIter) {
                    logger.info(">> stale for " + maxStaleIter + " iterations with " + sleepMillis + " ms and " + size + " size");
                    state.running = false;
                }
            }

            if (size == keyCount && finishMillis == -1) {
                finishMillis = System.currentTimeMillis();
                logger.info(">> " + (int) (finishMillis - startMillis));
                state.running = false;
            }

            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

//    @TimeStep(executionGroup="stress")
//    public void stressTheCluster(ThreadState state) {
//
//    }

    @Teardown(global = true)
    public void tearDown() throws ExecutionException, InterruptedException {
        syncMap.destroy();
        replyMap.put(0, 0);
        replyThatSyncFinished();
    }

    public static class ThreadState extends BaseThreadState {
        boolean running = true;
        int counter;
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

    private void replyThatSyncFinished() throws ExecutionException, InterruptedException {
        HazelcastClientInstanceImpl client = ((HazelcastClientProxy) targetInstance).client;
        ClientMessage request = MCWanSyncMapCodec.encodeRequest(wanReplicationName, wanPublisherId, 1, replyMapName);
        ClientInvocationFuture response = new ClientInvocation(client, request, "some-name", -1).invoke();
        response.get();
    }
}
