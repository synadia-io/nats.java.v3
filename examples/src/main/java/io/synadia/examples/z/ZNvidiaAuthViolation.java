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

import io.synadia.client.AuthHandler;
import io.synadia.client.Connection;
import io.synadia.client.Nats;
import io.synadia.client.Options;
import io.synadia.client.support.Debug;
import io.synadia.client.support.DebugListener;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

@SuppressWarnings("CallToPrintStackTrace")
public class ZNvidiaAuthViolation {

    private static final String user = "UBAX6GCZQYLJDLSNPBDDPLY6KIBRO2JAUYNPW4HCWBRCZ4OU57YQQQS3";
    private static final String userSeed = "SUAIUIHFQNVWSMKYGC4E5H5IEQZHHND3DKHTRKZWPCDXB6LXVD5R2KROSA";

    public static void main(String[] args) throws IOException, InterruptedException {
        Options options = getOptions();
        try (Connection connection = Nats.connect(options)) {
            Debug.info("RTT", connection.RTT());
            Thread.sleep(2000);
            options.getExecutor().submit(() -> onLameDuck(connection));
            Thread.sleep(Duration.ofSeconds(5).toMillis());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void onLameDuck(Connection conn) {
        try {
            Debug.info("LD 0", "client id {} starting force reconnect to nats", conn.getServerInfo().getClientId());

            // jitter
            Thread.sleep(new Random().nextInt(5000));
            if (conn.getStatus() == Connection.Status.CONNECTED) {
                // flush buffer then eagerly reconnect
                Debug.info("LD 1 FLUSH", "client id {} flushing", conn.getServerInfo().getClientId());
                conn.flush(Duration.ofSeconds(5));
            }
            // this may cause issues, but hopefully the active force
            // reconnection is a smaller error window than waiting to get
            // booted and detecting it normally.
            Debug.info("LD 2 B4", "client id {} force reconnecting to nats", conn.getServerInfo().getClientId());
            conn.forceReconnect();
            Debug.info("LD 3", "client id {} reconnected to nats", conn.getServerInfo().getClientId());
        } catch (Exception e) {
            Debug.info("LDEX", "client id {} ", conn.getServerInfo().getClientId(), e);
        }
    }

    public static Options getOptions()
        throws IOException, InterruptedException {
        Options options = Options.builder()
            .server("nats://localhost:4222")
            .authHandler(new AuthHandler() {
                private final AuthHandler delegate = Nats.staticCredentials(null,
                    userSeed.toCharArray());

                @Override
                public byte[] sign(byte[] nonce) {
                    byte[] sign = delegate.sign(nonce);
                    Debug.info("sign", "nats auth handler: sign nonce={} sign={}",
                        Arrays.toString(nonce),
                        Arrays.toString(sign));
                    return sign;
                }

                @Override
                public char[] getID() {
                    char[] id = delegate.getID();
                    Debug.info("getID", "nats auth handler: id={}", id == null ? "null" : new String(id));
                    return id;
                }

                @Override
                public char[] getJWT() {
                    char[] jwt = delegate.getJWT();
                    Debug.info("getJWT", "nats auth handler: jwt={}",
                        jwt == null ? "null" : new String(jwt));
                    return jwt;
                }
            })
            .pingInterval(Duration.ofSeconds(5))
            .useDispatcherWithExecutor()
            .reconnectWait(Duration.ofMillis(100))
            .errorListener(new DebugListener())
            .connectionListener(
                (conn, type) ->
                    Debug.info("CL", "nats connection event {} {}", type, conn.getServerInfo()))
            .build();
        return options;
    }
}
