package io.synadia.client.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ForceReconnectQueueCheckDataPort extends SocketDataPort {
    private static byte[] WRITE_CHECK;
    private static int WC_LEN;
    public static long DELAY;

    // Delays the actual socket close. The reader blocks in read() until the port closes, so this
    // controls how long a reader outlives the stop() that precedes the close in forceReconnectImpl.
    // Set it past the old 100ms join window to exercise the stale-reader race. 0 disables.
    public static long CLOSE_DELAY;

    public static void setCheck(String check) {
        WRITE_CHECK = check.getBytes(StandardCharsets.ISO_8859_1);
        WC_LEN = check.length();
    }

    // These are static, so a test that uses one behavior must not inherit the other's leftovers.
    public static void resetAll() {
        WRITE_CHECK = null;
        WC_LEN = 0;
        DELAY = 0;
        CLOSE_DELAY = 0;
    }

    // forceClose() delegates to close(), so overriding here covers both force-reconnect paths.
    @Override
    public void close() throws IOException {
        if (CLOSE_DELAY > 0) {
            try {
                Thread.sleep(CLOSE_DELAY);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        super.close();
    }

    @Override
    public void write(byte[] src, int toWrite) throws IOException {
        if (src.length >= WC_LEN) {
            boolean check = true;
            for (int x = 0; x < WC_LEN; x++) {
                if (src[x] != WRITE_CHECK[x]) {
                    check = false;
                    break;
                }
            }
            if (check) {
                try {
                    Thread.sleep(DELAY);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        super.write(src, toWrite);
    }
}
