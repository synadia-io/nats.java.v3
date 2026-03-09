// Copyright 2020 The NATS Authors
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

package io.synadia.examples.z;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ZBenchAnything {
    static final String RESULTS_HEADER = "                       | Elapsed ms      | Rounds       | Rounds/Time     | Time/Round        |\n";
    static final String RESULTS_SEP    = "-----------------------|-----------------|--------------|-----------------|-------------------|\n";
    static final String RESULTS = "%-22s | %15s | %12s | %14s | %17s |\n";

    static long ROUNDS = 100_000_000;
    static long REPORT_FREQUENCY = ROUNDS / 100;
    static int NUM_DIFF_BENCHES = 6;

    public static void main(String[] args) {
        long[] elapseds = new long[NUM_DIFF_BENCHES];
        for (int r = 1; r <= ROUNDS; r++) {
            elapseds[0] += arrayToList1();
            elapseds[1] += arrayToList2();
            elapseds[2] += listToArray1a();
            elapseds[3] += listToArray2a();
            elapseds[4] += listToArray1b();
            elapseds[5] += listToArray2b();
            if (r % REPORT_FREQUENCY == 0) {
                printResults(r, elapseds);
            }
        }

        System.out.println("\n");
        printResults(ROUNDS, elapseds);
    }

    private static void printResults(long rounds, long[] elapseds) {
//        long totalElapsed = 0;
//        for (long e : elapseds) {
//            totalElapsed += e;
//        }
        System.out.println();
        System.out.printf(RESULTS_HEADER + RESULTS_SEP);
        printResults("arrayToList1", elapseds[0], rounds);
        printResults("arrayToList2", elapseds[1], rounds);
        printResults("listToArray1a", elapseds[2], rounds);
        printResults("listToArray2a", elapseds[3], rounds);
        printResults("listToArray1b", elapseds[4], rounds);
        printResults("listToArray2b", elapseds[5], rounds);
//        System.out.printf(RESULTS_SEP);
//        printResults("Total/Average", totalElapsed, rounds * NUM_DIFF_BENCHES);
    }

    static String[] array1 = new String[]{"one", "two"};
    static String[] array2 = new String[]{"one", "two"};
    static List<String> list1 = Arrays.asList(array1);
    static List<String> list2 = Arrays.asList(array2);

    // ----------------------------------------------------------------------------------------------------
    // BENCHMARKS
    // ----------------------------------------------------------------------------------------------------
    public static long arrayToList1() {
        long start = System.nanoTime();
        List<String> list = Arrays.asList(array1);
        return System.nanoTime() - start;
    }

    public static long arrayToList2() {
        long start = System.nanoTime();
        List<String> list = Arrays.asList(array2);
        return System.nanoTime() - start;
    }

    public static long listToArray1a() {
        long start = System.nanoTime();
        String[] a = new String[list1.size()];
        list1.toArray(a);
        return System.nanoTime() - start;
    }

    public static long listToArray2a() {
        long start = System.nanoTime();
        String[] a = new String[list2.size()];
        list2.toArray(a);
        return System.nanoTime() - start;
    }

    public static long listToArray1b() {
        long start = System.nanoTime();
        String[] a = (String[]) list1.toArray();
        return System.nanoTime() - start;
    }

    public static long listToArray2b() {
        long start = System.nanoTime();
        String[] a = (String[]) list2.toArray();
        return System.nanoTime() - start;
    }

    // ----------------------------------------------------------------------------------------------------
    // RESULT HELPERS
    // ----------------------------------------------------------------------------------------------------
    private static void printResults(String label, Long elapsedNs, long rounds) {
        float fElapsedNs = elapsedNs.floatValue();
        float elapsedMs = fElapsedNs / 1_000_000F;
        String perTime = getOpsPerTime(fElapsedNs, elapsedMs, rounds);
        String timePer = getTimePerOps(fElapsedNs, elapsedMs, rounds);
        System.out.printf(RESULTS, label, format3(elapsedMs), format(rounds), perTime, timePer);
    }

    private static String getOpsPerTime(float elapsedNs, float elapsedMs, long rounds) {
        float nsPer = rounds / elapsedNs;
        float msPer = rounds / elapsedMs;
        return nsPer < 1F ? format3(msPer) + " r/ms" : format(nsPer) + " r/ns";
    }

    private static String getTimePerOps(float elapsedNs, float elapsedMs, long rounds) {
        float nsPer = elapsedNs/ rounds;
        float msPer = elapsedMs / rounds;
        return nsPer < 1F ? format3(msPer) + " ms/r" : format(nsPer) + " ns/r";
    }

    public static String format(Number s) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(s);
    }

    public static String format3(Number n) {
        if (n.longValue() >= 1_000_000_000) {
            return humanBytes(n.doubleValue());
        }
        String f = format(n);
        int at = f.indexOf('.');
        if (at == -1) {
            return f;
        }
        if (at == 0) {
            return f + "." + ZEROS.substring(0, 3);
        }
        return (f + ZEROS).substring(0, at + 3 + 1);
    }

    public static String humanBytes(double bytes) {
        if (bytes < HUMAN_BYTES_BASE) {
            return String.format("%.2f b", bytes);
        }
        int exp = (int) (Math.log(bytes) / Math.log(HUMAN_BYTES_BASE));
        try {
            return String.format("%.2f %s", bytes / Math.pow(HUMAN_BYTES_BASE, exp), HUMAN_BYTES_UNITS[exp]);
        }
        catch (Exception e) {
            return String.format("%.2f b", bytes);
        }
    }

    private static final String ZEROS = "000000000";
    private static final long HUMAN_BYTES_BASE = 1024;
    private static final String[] HUMAN_BYTES_UNITS = new String[] {"b", "kb", "mb", "gb", "tb", "pb", "eb"};
}
