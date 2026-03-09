package io.synadia.client.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ForceReconnectQueueCheckDataPort extends SocketDataPort {
    private static byte[] WRITE_CHECK;
    private static int WC_LEN;
    public static long DELAY;

    public static void setCheck(String check) {
        WRITE_CHECK = check.getBytes(StandardCharsets.ISO_8859_1);
        WC_LEN = check.length();
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
