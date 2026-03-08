package io.nats.compatibility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class Utility {
    public static File downloadToTempFile(String urlStr, String name, String ext) throws IOException {
        URL url = new URL(urlStr);
        File f = File.createTempFile(name, ext);
        ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
        try (FileOutputStream fileOutputStream = new FileOutputStream(f.getAbsolutePath())) {
            FileChannel fileChannel = fileOutputStream.getChannel();
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        }
        return f;
    }
}
