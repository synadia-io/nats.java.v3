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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.support.NatsConstants.EMPTY;
import static io.synadia.client.support.NatsJetStreamConstants.*;
import static io.synadia.client.support.Status.*;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ZByteArray {

    private static final String HAND_LABEL = "Current Hand Code  ";
    private static final String CLDE_LABEL = "Claude Manual Long ";
    private static final String BBBB_LABEL = "Claude Byte Buffer ";

    public static void main(String[] args) {
        List<ZByteArray> list = new ArrayList<>();

        load(list);


        long hand10k = 0;
        long clde10k = 0;
        long bbbb10k = 0;
        long hand100k = 0;
        long clde100k = 0;
        long bbbb100k = 0;
        long handMil = 0;
        long cldeMil = 0;
        long bbbbMil = 0;

        int rounds = 10;
        for (int x = 1; x <= rounds; x++) {
            System.out.println("\nRound: " + x);
            clde10k += claude(10_000, list);
            report(CLDE_LABEL, 10_000, clde10k / x);

            hand10k += hand(10_000, list);
            report(HAND_LABEL, 10_000, hand10k / x);

            bbbb10k += bbbb(10_000, list);
            report(BBBB_LABEL, 10_000, bbbb10k / x);

            clde100k += claude(100_000, list);
            report(CLDE_LABEL, 100_000, clde100k / x);

            hand100k += hand(100_000, list);
            report(HAND_LABEL, 100_000, hand100k / x);

            bbbb100k += bbbb(100_000, list);
            report(BBBB_LABEL, 100_000, bbbb100k / x);

            cldeMil += claude(1_000_000, list);
            report(CLDE_LABEL, 1_000_000, cldeMil / x);

            handMil += hand(1_000_000, list);
            report(HAND_LABEL, 1_000_000, handMil / x);

            bbbbMil += bbbb(1_000_000, list);
            report(BBBB_LABEL, 1_000_000, bbbbMil / x);
        }
    }

    private static void report(String label, int rounds, long elapsed) {
        System.out.println(label + "| Rounds: " + rounds + " | " + elapsed /1_000_000 + "ms | " + elapsed + "ns");
    }

    private static long hand(int rounds, List<ZByteArray> list) {
        long start = System.nanoTime();
        for (int x = 0; x < rounds; x++) {
            for (ZByteArray z : list) {
                z.getValueCheckKnownKeys();
                z.getValueCheckKnownStatuses();
            }
        }
        return System.nanoTime() - start;
    }

    private static long claude(int rounds, List<ZByteArray> list) {
        long start = System.nanoTime();
        for (int x = 0; x < rounds; x++) {
            for (ZByteArray z : list) {
                z.getValueCheckKnownKeysFast();
                z.getValueCheckKnownStatusesFast();
            }
        }
        return System.nanoTime() - start;
    }

    private static long bbbb(int rounds, List<ZByteArray> list) {
        long start = System.nanoTime();
        for (int x = 0; x < rounds; x++) {
            for (ZByteArray z : list) {
                z.getValueCheckKnownKeysBbbb();
                z.getValueCheckKnownStatusesBbbb();
            }
        }
        return System.nanoTime() - start;
    }

    public boolean bbEquals(byte[] checkBytes) {
        if (valueLength != checkBytes.length) return false;
        return ByteBuffer.wrap(serialized).equals(ByteBuffer.wrap(checkBytes));
    }
    public boolean fastEquals(byte[] checkBytes) {
        if (valueLength != checkBytes.length) return false;

        int length = serialized.length;
        int i = 0;

        // Compare 8 bytes at a time by converting to long
        int longLength = length - (length % 8);
        for (; i < longLength; i += 8) {
            int si = i + start;
            long aLong = ((long) serialized[i] << 56)
                | ((long) (serialized[si + 1] & 0xFF) << 48)
                | ((long) (serialized[si + 2] & 0xFF) << 40)
                | ((long) (serialized[si + 3] & 0xFF) << 32)
                | ((long) (serialized[si + 4] & 0xFF) << 24)
                | ((long) (serialized[si + 5] & 0xFF) << 16)
                | ((long) (serialized[si + 6] & 0xFF) << 8)
                | ((long) (serialized[si + 7] & 0xFF));

            long bLong = ((long) checkBytes[i] << 56)
                | ((long) (checkBytes[i + 1] & 0xFF) << 48)
                | ((long) (checkBytes[i + 2] & 0xFF) << 40)
                | ((long) (checkBytes[i + 3] & 0xFF) << 32)
                | ((long) (checkBytes[i + 4] & 0xFF) << 24)
                | ((long) (checkBytes[i + 5] & 0xFF) << 16)
                | ((long) (checkBytes[i + 6] & 0xFF) << 8)
                | ((long) (checkBytes[i + 7] & 0xFF));

            if (aLong != bLong) return false;
        }

        // Compare remaining bytes
        for (; i < length; i++) {
            if (serialized[i] != checkBytes[i]) return false;
        }

        return true;
    }

    int valueLength;
    byte[] serialized;
    int start = 0;
    boolean hasValue = true;

    public ZByteArray(String s) {
        serialized = s.getBytes(US_ASCII);
        valueLength = serialized.length;
    }

    @NonNull
    public String getValue() {
        return hasValue ? valueAsString() : EMPTY;
    }

    @Nullable
    public String getValueOrNull() {
        return hasValue ? valueAsString() : null;
    }

    private String valueAsString() {
        return new String(serialized, 0, valueLength, UTF_8).trim();
    }

    @NonNull
    public String getValueCheckKnownKeys() {
        if (valueLength == 0) {
            return EMPTY;
        }
        // all known keys are at least 5 characters Nats-<...> and KV-Operation
        if (valueLength > 5) {
            if (valueStartsNatsDash()) {
                if (endsMatch(NATS_STREAM_BYTES, 5)) {
                    return NATS_STREAM;
                }
                if (endsMatch(NATS_SEQUENCE_BYTES, 5)) {
                    return NATS_SEQUENCE;
                }
                if (endsMatch(NATS_TIMESTAMP_BYTES, 5)) {
                    return NATS_TIMESTAMP;
                }
                if (endsMatch(NATS_SUBJECT_BYTES, 5)) {
                    return NATS_SUBJECT;
                }
                if (endsMatch(NATS_LAST_SEQUENCE_BYTES, 5)) {
                    return NATS_LAST_SEQUENCE;
                }
                if (endsMatch(NATS_NUM_PENDING_BYTES, 5)) {
                    return NATS_NUM_PENDING;
                }
                if (endsMatch(CONSUMER_STALLED_HDR_BYTES, 5)) {
                    return CONSUMER_STALLED_HDR;
                }
                if (endsMatch(MSG_SIZE_HDR_BYTES, 5)) {
                    return MSG_SIZE_HDR;
                }
                if (endsMatch(NATS_MARKER_REASON_HDR_BYTES, 5)) {
                    return NATS_MARKER_REASON_HDR;
                }
                if (endsMatch(NATS_PENDING_MESSAGES_BYTES, 5)) {
                    return NATS_PENDING_MESSAGES;
                }
                if (endsMatch(NATS_PENDING_BYTES_BYTES, 5)) {
                    return NATS_PENDING_BYTES;
                }
            }
            else if (endsMatch(KV_OPERATION_HEADER_KEY_BYTES, 0)) {
                return KV_OPERATION_HEADER_KEY;
            }
        }

        // didn't know the key
        return valueAsString();
    }

    @NonNull
    public String getValueCheckKnownKeysFast() {
        if (valueLength == 0) {
            return EMPTY;
        }
        // all known keys are at least 5 characters Nats-<...> and KV-Operation
        if (valueLength > 5) {
            if (fastEquals(NATS_STREAM_BYTES)) {
                return NATS_STREAM;
            }
            if (fastEquals(NATS_SEQUENCE_BYTES)) {
                return NATS_SEQUENCE;
            }
            if (fastEquals(NATS_TIMESTAMP_BYTES)) {
                return NATS_TIMESTAMP;
            }
            if (fastEquals(NATS_SUBJECT_BYTES)) {
                return NATS_SUBJECT;
            }
            if (fastEquals(NATS_LAST_SEQUENCE_BYTES)) {
                return NATS_LAST_SEQUENCE;
            }
            if (fastEquals(NATS_NUM_PENDING_BYTES)) {
                return NATS_NUM_PENDING;
            }
            if (fastEquals(CONSUMER_STALLED_HDR_BYTES)) {
                return CONSUMER_STALLED_HDR;
            }
            if (fastEquals(MSG_SIZE_HDR_BYTES)) {
                return MSG_SIZE_HDR;
            }
            if (fastEquals(NATS_MARKER_REASON_HDR_BYTES)) {
                return NATS_MARKER_REASON_HDR;
            }
            if (fastEquals(NATS_PENDING_MESSAGES_BYTES)) {
                return NATS_PENDING_MESSAGES;
            }
            if (fastEquals(NATS_PENDING_BYTES_BYTES)) {
                return NATS_PENDING_BYTES;
            }
            if (fastEquals(KV_OPERATION_HEADER_KEY_BYTES)) {
                return KV_OPERATION_HEADER_KEY;
            }
        }

        // didn't know the key
        return valueAsString();
    }

    @NonNull
    public String getValueCheckKnownKeysBbbb() {
        if (valueLength == 0) {
            return EMPTY;
        }
        // all known keys are at least 5 characters Nats-<...> and KV-Operation
        if (valueLength > 5) {
            if (bbEquals(NATS_STREAM_BYTES)) {
                return NATS_STREAM;
            }
            if (bbEquals(NATS_SEQUENCE_BYTES)) {
                return NATS_SEQUENCE;
            }
            if (bbEquals(NATS_TIMESTAMP_BYTES)) {
                return NATS_TIMESTAMP;
            }
            if (bbEquals(NATS_SUBJECT_BYTES)) {
                return NATS_SUBJECT;
            }
            if (bbEquals(NATS_LAST_SEQUENCE_BYTES)) {
                return NATS_LAST_SEQUENCE;
            }
            if (bbEquals(NATS_NUM_PENDING_BYTES)) {
                return NATS_NUM_PENDING;
            }
            if (bbEquals(CONSUMER_STALLED_HDR_BYTES)) {
                return CONSUMER_STALLED_HDR;
            }
            if (bbEquals(MSG_SIZE_HDR_BYTES)) {
                return MSG_SIZE_HDR;
            }
            if (bbEquals(NATS_MARKER_REASON_HDR_BYTES)) {
                return NATS_MARKER_REASON_HDR;
            }
            if (bbEquals(NATS_PENDING_MESSAGES_BYTES)) {
                return NATS_PENDING_MESSAGES;
            }
            if (bbEquals(NATS_PENDING_BYTES_BYTES)) {
                return NATS_PENDING_BYTES;
            }
            if (bbEquals(KV_OPERATION_HEADER_KEY_BYTES)) {
                return KV_OPERATION_HEADER_KEY;
            }
        }

        // didn't know the key
        return valueAsString();
    }

    @Nullable
    public String getValueCheckKnownStatuses() {
        if (valueLength == 0) {
            return null;
        }
        if (valueLength > 11) {
            switch (serialized[start]) {
                case 'E':
                    if (valueStartsWithExceededMax()) {
                        if (endsMatch(EXCEEDED_MAX_WAITING_BYTES, 8)) {
                            return EXCEEDED_MAX_WAITING;
                        }
                        if (endsMatch(EXCEEDED_MAX_REQUEST_BATCH_BYTES, 8)) {
                            return EXCEEDED_MAX_REQUEST_BATCH;
                        }
                        if (endsMatch(EXCEEDED_MAX_REQUEST_EXPIRES_BYTES, 8)) {
                            return EXCEEDED_MAX_REQUEST_EXPIRES;
                        }
                        if (endsMatch(EXCEEDED_MAX_REQUEST_MAX_BYTES_BYTES, 8)) {
                            return EXCEEDED_MAX_REQUEST_MAX_BYTES;
                        }
                    }
                    break;
                case 'B':
                    if (endsMatch(BATCH_COMPLETED_BYTES, 1)) {
                        return BATCH_COMPLETED;
                    }
                    if (endsMatch(BAD_REQUEST_BYTES, 1)) {
                        return BAD_REQUEST;
                    }
                    break;
                case 'N':
                    if (endsMatch(NO_RESPONDERS_TEXT_BYTES, 1)) {
                        return NO_RESPONDERS_TEXT;
                    }
                    if (endsMatch(NO_MESSAGES_BYTES, 1)) {
                        return NO_MESSAGES;
                    }
                    break;
                case 'F':
                    if (endsMatch(FLOW_CONTROL_TEXT_BYTES, 1)) {
                        return FLOW_CONTROL_TEXT;
                    }
                    break;
                case 'I':
                    if (endsMatch(HEARTBEAT_TEXT_BYTES, 1)) {
                        return HEARTBEAT_TEXT;
                    }
                    break;
                case 'M':
                    if (endsMatch(MESSAGE_SIZE_EXCEEDS_MAX_BYTES_BYTES, 1)) {
                        return MESSAGE_SIZE_EXCEEDS_MAX_BYTES;
                    }
                    break;
                case 'L':
                    if (endsMatch(LEADERSHIP_CHANGE_BYTES, 1)) {
                        return LEADERSHIP_CHANGE;
                    }
                    break;
                case 'S':
                    if (endsMatch(SERVER_SHUTDOWN_BYTES, 1)) {
                        return SERVER_SHUTDOWN;
                    }
                    break;
                case 'C':
                    if (endsMatch(CONSUMER_DELETED_BYTES, 1)) {
                        return CONSUMER_DELETED;
                    }
                    if (endsMatch(CONSUMER_IS_PUSH_BASED_BYTES, 1)) {
                        return CONSUMER_IS_PUSH_BASED;
                    }
                    break;
            }
        }
        else if (endsMatch(EOB_TEXT_BYTES, 0)) { // only short status
            return EOB_TEXT;
        }
        return valueAsString();
    }

    @Nullable
    public String getValueCheckKnownStatusesFast() {
        if (valueLength == 0) {
            return null;
        }
        if (valueLength > 11) {
            switch (serialized[start]) {
                case 'E':
                    if (valueStartsWithExceededMax()) {
                        if (fastEquals(EXCEEDED_MAX_WAITING_BYTES)) {
                            return EXCEEDED_MAX_WAITING;
                        }
                        if (fastEquals(EXCEEDED_MAX_REQUEST_BATCH_BYTES)) {
                            return EXCEEDED_MAX_REQUEST_BATCH;
                        }
                        if (fastEquals(EXCEEDED_MAX_REQUEST_EXPIRES_BYTES)) {
                            return EXCEEDED_MAX_REQUEST_EXPIRES;
                        }
                        if (fastEquals(EXCEEDED_MAX_REQUEST_MAX_BYTES_BYTES)) {
                            return EXCEEDED_MAX_REQUEST_MAX_BYTES;
                        }
                    }
                    break;
                case 'B':
                    if (fastEquals(BATCH_COMPLETED_BYTES)) {
                        return BATCH_COMPLETED;
                    }
                    if (fastEquals(BAD_REQUEST_BYTES)) {
                        return BAD_REQUEST;
                    }
                    break;
                case 'N':
                    if (fastEquals(NO_RESPONDERS_TEXT_BYTES)) {
                        return NO_RESPONDERS_TEXT;
                    }
                    if (fastEquals(NO_MESSAGES_BYTES)) {
                        return NO_MESSAGES;
                    }
                    break;
                case 'F':
                    if (fastEquals(FLOW_CONTROL_TEXT_BYTES)) {
                        return FLOW_CONTROL_TEXT;
                    }
                    break;
                case 'I':
                    if (fastEquals(HEARTBEAT_TEXT_BYTES)) {
                        return HEARTBEAT_TEXT;
                    }
                    break;
                case 'M':
                    if (fastEquals(MESSAGE_SIZE_EXCEEDS_MAX_BYTES_BYTES)) {
                        return MESSAGE_SIZE_EXCEEDS_MAX_BYTES;
                    }
                    break;
                case 'L':
                    if (fastEquals(LEADERSHIP_CHANGE_BYTES)) {
                        return LEADERSHIP_CHANGE;
                    }
                    break;
                case 'S':
                    if (fastEquals(SERVER_SHUTDOWN_BYTES)) {
                        return SERVER_SHUTDOWN;
                    }
                    break;
                case 'C':
                    if (fastEquals(CONSUMER_DELETED_BYTES)) {
                        return CONSUMER_DELETED;
                    }
                    if (fastEquals(CONSUMER_IS_PUSH_BASED_BYTES)) {
                        return CONSUMER_IS_PUSH_BASED;
                    }
                    break;
            }
        }
        else if (fastEquals(EOB_TEXT_BYTES)) { // only short status
            return EOB_TEXT;
        }
        return valueAsString();
    }

    @Nullable
    public String getValueCheckKnownStatusesBbbb() {
        if (valueLength == 0) {
            return null;
        }
        if (valueLength > 11) {
            switch (serialized[start]) {
                case 'E':
                    if (valueStartsWithExceededMax()) {
                        if (bbEquals(EXCEEDED_MAX_WAITING_BYTES)) {
                            return EXCEEDED_MAX_WAITING;
                        }
                        if (bbEquals(EXCEEDED_MAX_REQUEST_BATCH_BYTES)) {
                            return EXCEEDED_MAX_REQUEST_BATCH;
                        }
                        if (bbEquals(EXCEEDED_MAX_REQUEST_EXPIRES_BYTES)) {
                            return EXCEEDED_MAX_REQUEST_EXPIRES;
                        }
                        if (bbEquals(EXCEEDED_MAX_REQUEST_MAX_BYTES_BYTES)) {
                            return EXCEEDED_MAX_REQUEST_MAX_BYTES;
                        }
                    }
                    break;
                case 'B':
                    if (bbEquals(BATCH_COMPLETED_BYTES)) {
                        return BATCH_COMPLETED;
                    }
                    if (bbEquals(BAD_REQUEST_BYTES)) {
                        return BAD_REQUEST;
                    }
                    break;
                case 'N':
                    if (bbEquals(NO_RESPONDERS_TEXT_BYTES)) {
                        return NO_RESPONDERS_TEXT;
                    }
                    if (bbEquals(NO_MESSAGES_BYTES)) {
                        return NO_MESSAGES;
                    }
                    break;
                case 'F':
                    if (bbEquals(FLOW_CONTROL_TEXT_BYTES)) {
                        return FLOW_CONTROL_TEXT;
                    }
                    break;
                case 'I':
                    if (bbEquals(HEARTBEAT_TEXT_BYTES)) {
                        return HEARTBEAT_TEXT;
                    }
                    break;
                case 'M':
                    if (bbEquals(MESSAGE_SIZE_EXCEEDS_MAX_BYTES_BYTES)) {
                        return MESSAGE_SIZE_EXCEEDS_MAX_BYTES;
                    }
                    break;
                case 'L':
                    if (bbEquals(LEADERSHIP_CHANGE_BYTES)) {
                        return LEADERSHIP_CHANGE;
                    }
                    break;
                case 'S':
                    if (bbEquals(SERVER_SHUTDOWN_BYTES)) {
                        return SERVER_SHUTDOWN;
                    }
                    break;
                case 'C':
                    if (bbEquals(CONSUMER_DELETED_BYTES)) {
                        return CONSUMER_DELETED;
                    }
                    if (bbEquals(CONSUMER_IS_PUSH_BASED_BYTES)) {
                        return CONSUMER_IS_PUSH_BASED;
                    }
                    break;
            }
        }
        else if (bbEquals(EOB_TEXT_BYTES)) { // only short status
            return EOB_TEXT;
        }
        return valueAsString();
    }

    static final byte[] NATS_DASH_PREFIX_BYTES = "Nats-".getBytes();
    static final byte[] EXCEEDED_MAX_PREFIX_BYTES = EXCEEDED_MAX_PREFIX.getBytes();
    static final int EXCEEDED_MAX_PREFIX_BYTES_LEN = EXCEEDED_MAX_PREFIX.length();

    private boolean valueStartsNatsDash() {
        for (int i = 0; i < 4; i++) {
            if (NATS_DASH_PREFIX_BYTES[i] != serialized[start + i]) {
                return false;
            }
        }
        return true;
    }

    private boolean valueStartsWithExceededMax() {
        // we know we already checked the first letter to be E
        for (int i = 1; i < EXCEEDED_MAX_PREFIX_BYTES_LEN; i++) {
            if (EXCEEDED_MAX_PREFIX_BYTES[i] != serialized[start + i]) {
                return false;
            }
        }
        return true;
    }

    private boolean endsMatch(byte @NonNull [] checkBytes, int compareStartIndex) {
        if (valueLength != checkBytes.length) {
            return false;
        }
        for (int i = compareStartIndex; i < valueLength; i++) {
            if (checkBytes[i] != serialized[start + i]) {
                return false;
            }
        }

        return true;
    }

    private static void load(List<ZByteArray> list) {
        list.add(new ZByteArray((NATS_SUBJECT)));
        list.add(new ZByteArray((NATS_SEQUENCE)));
        list.add(new ZByteArray((NATS_TIMESTAMP)));
        list.add(new ZByteArray((NATS_STREAM)));
        list.add(new ZByteArray((NATS_LAST_SEQUENCE)));
        list.add(new ZByteArray((NATS_NUM_PENDING)));
        list.add(new ZByteArray((CONSUMER_STALLED_HDR)));
        list.add(new ZByteArray((MSG_SIZE_HDR)));
        list.add(new ZByteArray((NATS_MARKER_REASON_HDR)));
        list.add(new ZByteArray((NATS_PENDING_MESSAGES)));
        list.add(new ZByteArray((NATS_PENDING_BYTES)));
        list.add(new ZByteArray((KV_OPERATION_HEADER_KEY)));
        list.add(new ZByteArray((NATS_SUBJECT)));
        list.add(new ZByteArray((NATS_SEQUENCE)));
        list.add(new ZByteArray((NATS_TIMESTAMP)));
        list.add(new ZByteArray((NATS_STREAM)));
        list.add(new ZByteArray((NATS_LAST_SEQUENCE)));
        list.add(new ZByteArray((NATS_NUM_PENDING)));
        list.add(new ZByteArray((CONSUMER_STALLED_HDR)));
        list.add(new ZByteArray((MSG_SIZE_HDR)));
        list.add(new ZByteArray((NATS_MARKER_REASON_HDR)));
        list.add(new ZByteArray((NATS_PENDING_MESSAGES)));
        list.add(new ZByteArray((NATS_PENDING_BYTES)));
        list.add(new ZByteArray((KV_OPERATION_HEADER_KEY)));
        list.add(new ZByteArray((NATS_SUBJECT)));
        list.add(new ZByteArray((NATS_SEQUENCE)));
        list.add(new ZByteArray((NATS_TIMESTAMP)));
        list.add(new ZByteArray((NATS_STREAM)));
        list.add(new ZByteArray((NATS_LAST_SEQUENCE)));
        list.add(new ZByteArray((NATS_NUM_PENDING)));
        list.add(new ZByteArray((CONSUMER_STALLED_HDR)));
        list.add(new ZByteArray((MSG_SIZE_HDR)));
        list.add(new ZByteArray((NATS_MARKER_REASON_HDR)));
        list.add(new ZByteArray((NATS_PENDING_MESSAGES)));
        list.add(new ZByteArray((NATS_PENDING_BYTES)));
        list.add(new ZByteArray((KV_OPERATION_HEADER_KEY)));
        list.add(new ZByteArray(("N-Starts-With-Known-Byte")));
        list.add(new ZByteArray(("K-Starts-With-Known-Byte")));
        list.add(new ZByteArray(("X-Starts-With-Unknown-Byte")));
        list.add(new ZByteArray((EXCEEDED_MAX_WAITING)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_BATCH)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_MAX_BYTES)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_EXPIRES)));
        list.add(new ZByteArray((EOB_TEXT)));
        list.add(new ZByteArray((BATCH_COMPLETED)));
        list.add(new ZByteArray((BAD_REQUEST)));
        list.add(new ZByteArray((NO_RESPONDERS_TEXT)));
        list.add(new ZByteArray((NO_MESSAGES)));
        list.add(new ZByteArray((FLOW_CONTROL_TEXT)));
        list.add(new ZByteArray((HEARTBEAT_TEXT)));
        list.add(new ZByteArray((MESSAGE_SIZE_EXCEEDS_MAX_BYTES)));
        list.add(new ZByteArray((LEADERSHIP_CHANGE)));
        list.add(new ZByteArray((SERVER_SHUTDOWN)));
        list.add(new ZByteArray((CONSUMER_DELETED)));
        list.add(new ZByteArray((CONSUMER_IS_PUSH_BASED)));
        list.add(new ZByteArray((EXCEEDED_MAX_WAITING)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_BATCH)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_MAX_BYTES)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_EXPIRES)));
        list.add(new ZByteArray((EOB_TEXT)));
        list.add(new ZByteArray((BATCH_COMPLETED)));
        list.add(new ZByteArray((BAD_REQUEST)));
        list.add(new ZByteArray((NO_RESPONDERS_TEXT)));
        list.add(new ZByteArray((NO_MESSAGES)));
        list.add(new ZByteArray((FLOW_CONTROL_TEXT)));
        list.add(new ZByteArray((HEARTBEAT_TEXT)));
        list.add(new ZByteArray((MESSAGE_SIZE_EXCEEDS_MAX_BYTES)));
        list.add(new ZByteArray((LEADERSHIP_CHANGE)));
        list.add(new ZByteArray((SERVER_SHUTDOWN)));
        list.add(new ZByteArray((CONSUMER_DELETED)));
        list.add(new ZByteArray((CONSUMER_IS_PUSH_BASED)));
        list.add(new ZByteArray((EXCEEDED_MAX_WAITING)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_BATCH)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_MAX_BYTES)));
        list.add(new ZByteArray((EXCEEDED_MAX_REQUEST_EXPIRES)));
        list.add(new ZByteArray((EOB_TEXT)));
        list.add(new ZByteArray((BATCH_COMPLETED)));
        list.add(new ZByteArray((BAD_REQUEST)));
        list.add(new ZByteArray((NO_RESPONDERS_TEXT)));
        list.add(new ZByteArray((NO_MESSAGES)));
        list.add(new ZByteArray((FLOW_CONTROL_TEXT)));
        list.add(new ZByteArray((HEARTBEAT_TEXT)));
        list.add(new ZByteArray((MESSAGE_SIZE_EXCEEDS_MAX_BYTES)));
        list.add(new ZByteArray((LEADERSHIP_CHANGE)));
        list.add(new ZByteArray((SERVER_SHUTDOWN)));
        list.add(new ZByteArray((CONSUMER_DELETED)));
        list.add(new ZByteArray((CONSUMER_IS_PUSH_BASED)));
        list.add(new ZByteArray(("E Test Starts With Known Letter But Not Known")));
        list.add(new ZByteArray(("B Test Starts With Known Letter But Not Known")));
        list.add(new ZByteArray(("N Test Starts With Known Letter But Not Known")));
        list.add(new ZByteArray(("F Test Starts With Known Letter But Not Known")));
        list.add(new ZByteArray(("I Test Starts With Known Letter But Not Known")));
        list.add(new ZByteArray(("M Test Starts With Known Letter But Not Known")));
        list.add(new ZByteArray(("L Test Starts With Known Letter But Not Known")));
        list.add(new ZByteArray(("S Test Starts With Known Letter But Not Known")));
        list.add(new ZByteArray(("C Test Starts With Known Letter But Not Known")));
    }
}

/*

Current Hand Code  | Rounds: 10 x 10_000 | Avg: 46ms (46232150ns)
Claude Byte Buffer | Rounds: 10 x 10_000 | Avg: 60ms (60418420ns)
Claude Manual Long | Rounds: 10 x 10_000 | Avg: 61ms (61168160ns)

Current Hand Code  | Rounds: 10 x 100_000 | Avg: 437ms (437607800ns)
Claude Byte Buffer | Rounds: 10 x 100_000 | Avg: 528ms (528989500ns)
Claude Manual Long | Rounds: 10 x 100_000 | Avg: 595ms (595680890ns)

Current Hand Code  | Rounds: 10 x 1_000_000 | Avg: 4248ms (4248489680ns
Claude Byte Buffer | Rounds: 10 x 1_000_000 | Avg: 5292ms (5292705770ns))
Claude Manual Long | Rounds: 10 x 1_000_000 | Avg: 5691ms (5691301620ns)


 */