// Copyright 2025 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.client.utils;

import org.junit.jupiter.api.TestInfo;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public abstract class TestDebugger {
    static ReentrantLock lock = new ReentrantLock();
    static PrintStream out;
    static List<String> classes = new ArrayList<>();

    static {
        lock.lock();
        try {
            out = new PrintStream("C:\\temp\\jnats-test.txt");
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        finally {
            lock.unlock();
        }
        final Thread shutdownHookThread = new Thread("TestDebugger-Shutdown-Hook") {
            @Override
            public void run() {
                out.println();
                out.println();
                out.println();
                out.println();
                for (String c : classes) {
                    out.println(c);
                }
                out.flush();
                out.close();
            }
        };
        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
    }

    public static void info(TestInfo info) {
        lock.lock();
        try {
            if (info.getTestClass().isPresent()) {
                record(info.getTestClass().get().getName());
            }
            else if (info.getDisplayName() != null) {
                record(info.getDisplayName());
            }
        }
        finally {
            lock.unlock();
        }
    }

    private static void record(String text) {
        if (!classes.contains(text)) {
            out.println(text);
            out.flush();
        }
    }
}
