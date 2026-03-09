package io.synadia.examples.z;

import io.synadia.client.impl.SocketDataPort;

import java.io.IOException;

public class ZForceReconnectQueueCheckDataPort extends SocketDataPort {
    static String writeCheck;
    static long delay;

    @Override
    public void write(byte[] src, int toWrite) throws IOException {
        String s = new String(src, 0, Math.min(7, toWrite));
        if (s.startsWith(writeCheck)) {
            try {
                Thread.sleep(delay);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        super.write(src, toWrite);
    }
}
