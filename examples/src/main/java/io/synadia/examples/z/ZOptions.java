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

import io.synadia.client.*;

import java.time.Duration;

public class ZOptions {
    public static void main(String[] args) {
// Options
//
// - connectionTimeout:
//   = Default is 2. TLS needs more time
//   = Should take into consideration RTT, TLS processing time and possible server load
//
// - reconnectWait:
//   = Default is 2 seconds
//   = This is the time that a connection will always wait before retrying
//
// - reconnectJitterTls:
//   = Default is 1000 millis.
//   = This value is used to get a random number of millis from 0 to reconnectJitterTls,
//     to be added to the reconnectWait
//
// - reconnectDelayHandler:
//   = If you provide this, it replaces reconnectWait/reconnectJitterTls with
//     a complete custom implementation. See ReconnectDelayHandler interface
//
// JetStreamOptions
// - requestTimeout:
//   = The amount of time to wait for JetStream api request-response calls
//     to be submitted and to complete.
//   = Client 2.16.14 has a bug fix specific to consumer create using Options
//     connectionTimeout instead of JetStreamOptions requestTimeout
//   = Adjust this for your RTT plus some for server processing time

        Options options = Options.builder()
            .server("tls://host1:4222,tls://host2:4222,tls://host3:4222")
            .connectionTimeout(Duration.ofSeconds(30))
            .reconnectWait(Duration.ofSeconds(15))
            .reconnectJitterTls(Duration.ofMillis(10_000))
            // .reconnectDelayHandler(...)
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamOptions jso = JetStreamOptions.builder()
                .requestTimeout(Duration.ofSeconds(10))
                .build();
            JetStream js = nc.jetStream(jso);
        }
        catch (Exception e) {
        }
    }
}
