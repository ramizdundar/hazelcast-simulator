/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.simulator.test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

public enum TestPhase {
    SETUP("setup"),
    LOCAL_WARMUP("local warmup"),
    GLOBAL_WARMUP("global warmup"),
    RUN("run"),
    GLOBAL_VERIFY("global verify"),
    LOCAL_VERIFY("local verify"),
    GLOBAL_TEARDOWN("global tear down"),
    LOCAL_TEARDOWN("local tear down");

    private final String description;

    TestPhase(String description) {
        this.description = description;
    }

    public String desc() {
        return description;
    }

    public static ConcurrentMap<TestPhase, CountDownLatch> getTestPhaseSyncMap(boolean isParallel, int testCount,
                                                                               TestPhase latestTestPhaseToSync) {
        if (!isParallel) {
            return null;
        }
        ConcurrentMap<TestPhase, CountDownLatch> testPhaseSyncMap = new ConcurrentHashMap<TestPhase, CountDownLatch>();
        boolean useTestCount = true;
        for (TestPhase testPhase : TestPhase.values()) {
            testPhaseSyncMap.put(testPhase, new CountDownLatch(useTestCount ? testCount : 0));
            if (testPhase == latestTestPhaseToSync) {
                useTestCount = false;
            }
        }
        return testPhaseSyncMap;
    }
}
