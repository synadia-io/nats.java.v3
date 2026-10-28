package io.synadia.service;

import io.nats.json.DateTimeUtils;
import io.nats.json.JsonParser;
import io.nats.json.JsonSerializable;
import io.nats.json.JsonValue;
import io.synadia.client.Dispatcher;
import io.synadia.client.Message;
import io.synadia.client.Options;
import io.synadia.client.impl.*;
import io.synadia.client.utils.TestBase;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.nats.json.JsonValueUtils.readInteger;
import static io.nats.json.JsonValueUtils.readString;
import static io.nats.json.JsonWriteUtils.toKey;
import static io.synadia.client.impl.NatsPackageScopeWorkarounds.getDispatchers;
import static io.synadia.client.utils.NatsConstants.DOT;
import static io.synadia.client.utils.OptionsUtils.options;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static io.synadia.service.Service.SRV_PING;
import static io.synadia.service.ServiceMessage.NATS_SERVICE_ERROR;
import static io.synadia.service.ServiceMessage.NATS_SERVICE_ERROR_CODE;
import static org.junit.jupiter.api.Assertions.*;

public class ServiceTests extends TestBase {

    public static final String SERVICE_TESTS_SHARED_NAME = "ServiceTests";

    @Test
    public void testServiceWorkflow() throws Exception {
        String serviceName1 = "Service1" + random();
        String serviceName2 = "Service2" + random();
        String echoEndpointName = "EchoEndpoint" + random();
        String echoEndpointSubject = "echo" + random();
        String sortGroupName = "sort" + random();
        String sortEndpointAscendingName = "SortEndpointAscending" + random();
        String sortEndpointDescendingName = "SortEndpointDescending" + random();
        String sortEndpointAscendingSubject = "ascending" + random();
        String sortEndpointDescendingSubject = "descending" + random();
        String reverseEndpointName = "ReverseEndpoint" + random();
        String reverseEndpointSubject = "reverse" + random();
        String customQgroup = "customQ" + random();

        Map<String, String> verifyMap = new HashMap<>();
        verifyMap.put(echoEndpointName, "echoEndpointName");
        verifyMap.put(sortEndpointAscendingName, "sortEndpointAscendingName");
        verifyMap.put(sortEndpointDescendingName, "sortEndpointDescendingName");
        verifyMap.put(reverseEndpointName, "reverseEndpointName");

        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection clientNc = SharedServer.sharedConnectionForServer(ts);
            NatsConnection serviceNc1 = SharedServer.sharedConnectionForServer(ts);
            NatsConnection serviceNc2 = SharedServer.sharedConnectionForServer(ts);

            Endpoint endEcho = Endpoint.builder()
                .name(echoEndpointName)
                .subject(echoEndpointSubject)
                .queueGroup(customQgroup)
                .build();

            Endpoint endSortA = Endpoint.builder()
                .name(sortEndpointAscendingName)
                .subject(sortEndpointAscendingSubject)
                .build();

            // constructor coverage
            Endpoint endSortD = new Endpoint(
                sortEndpointDescendingName,
                sortEndpointDescendingSubject);

            // sort is going to be grouped
            Group sortGroup = new Group(sortGroupName);

            ServiceEndpoint seEcho1 = ServiceEndpoint.builder()
                .endpoint(endEcho)
                .handler(new EchoHandler(serviceNc1))
                .statsDataSupplier(ServiceTests::supplyData)
                .build();

            ServiceEndpoint seSortA1 = ServiceEndpoint.builder()
                .group(sortGroup)
                .endpoint(endSortA)
                .handler(new SortHandlerA(serviceNc1))
                .build();

            ServiceEndpoint seSortD1 = ServiceEndpoint.builder()
                .group(sortGroup)
                .endpoint(endSortD)
                .handler(new SortHandlerD(serviceNc1))
                .build();

            ServiceEndpoint seEcho2 = ServiceEndpoint.builder()
                .endpoint(endEcho)
                .handler(new EchoHandler(serviceNc2))
                .statsDataSupplier(ServiceTests::supplyData)
                .build();

            // build variations
            ServiceEndpoint seSortA2 = ServiceEndpoint.builder()
                .group(sortGroup)
                .endpointName(endSortA.getName())
                .endpointSubject(endSortA.getSubject())
                .handler(new SortHandlerA(serviceNc2))
                .build();

            ServiceEndpoint seSortD2 = ServiceEndpoint.builder()
                .group(sortGroup)
                .endpointName(endSortD.getName())
                .endpointSubject(endSortD.getSubject())
                .handler(new SortHandlerD(serviceNc2))
                .build();

            Service service1 = new ServiceBuilder()
                .name(serviceName1)
                .version("1.0.0")
                .connection(serviceNc1)
                .addServiceEndpoint(seEcho1)
                .addServiceEndpoint(seSortA1)
                .addServiceEndpoint(seSortD1)
                .build();
            String serviceId1 = service1.getId();
            CompletableFuture<Boolean> serviceStoppedFuture1 = service1.startService();

            Service service2 = new ServiceBuilder()
                .name(serviceName2)
                .version("1.0.0")
                .connection(serviceNc2)
                .addServiceEndpoint(seEcho2)
                .addServiceEndpoint(seSortA2)
                .addServiceEndpoint(seSortD2)
                .build();
            String serviceId2 = service2.getId();
            CompletableFuture<Boolean> serviceStoppedFuture2 = service2.startService();

            assertNotEquals(serviceId1, serviceId2);

            sleep(1000); // just make sure services are all started, for slow CI machines

            // service request execution
            int requestCount = 10;
            for (int x = 0; x < requestCount; x++) {
                verifyServiceExecution(clientNc, echoEndpointName, echoEndpointSubject, null, verifyMap);
                verifyServiceExecution(clientNc, sortEndpointAscendingName, sortEndpointAscendingSubject, sortGroup, verifyMap);
                verifyServiceExecution(clientNc, sortEndpointDescendingName, sortEndpointDescendingSubject, sortGroup, verifyMap);
            }

            PingResponse pingResponse1 = service1.getPingResponse();
            PingResponse pingResponse2 = service2.getPingResponse();
            InfoResponse infoResponse1 = service1.getInfoResponse();
            InfoResponse infoResponse2 = service2.getInfoResponse();
            StatsResponse statsResponse1 = service1.getStatsResponse();
            StatsResponse statsResponse2 = service2.getStatsResponse();
            EndpointStats[] endpointStatsArray1 = new EndpointStats[]{
                service1.getEndpointStats(echoEndpointName),
                service1.getEndpointStats(sortEndpointAscendingName),
                service1.getEndpointStats(sortEndpointDescendingName)
            };
            EndpointStats[] endpointStatsArray2 = new EndpointStats[]{
                service2.getEndpointStats(echoEndpointName),
                service2.getEndpointStats(sortEndpointAscendingName),
                service2.getEndpointStats(sortEndpointDescendingName)
            };
            assertNull(service1.getEndpointStats("notAnEndpoint"));

            assertEquals(serviceId1, pingResponse1.getId());
            assertEquals(serviceId2, pingResponse2.getId());
            assertEquals(serviceId1, infoResponse1.getId());
            assertEquals(serviceId2, infoResponse2.getId());
            assertEquals(serviceId1, statsResponse1.getId());
            assertEquals(serviceId2, statsResponse2.getId());

            // this relies on the fact that I load the endpoints up in the service
            // in the same order and the json list comes back ordered
            // expecting 10 responses across each endpoint between 2 services
            for (int x = 0; x < 3; x++) {
                assertEquals(requestCount,
                    endpointStatsArray1[x].getNumRequests()
                        + endpointStatsArray2[x].getNumRequests());
                assertEquals(requestCount,
                    statsResponse1.getEndpointStatsList().get(x).getNumRequests()
                        + statsResponse2.getEndpointStatsList().get(x).getNumRequests());
            }

            // discovery - wait at most 500 millis for responses, 5 total responses max
            Discovery discovery = new Discovery(clientNc, 1500, 5);

            // ping discovery
            Verifier pingVerifier = (expected, response) -> assertInstanceOf(PingResponse.class, response);
            verifyDiscovery(discovery.ping(), pingVerifier, pingResponse1, pingResponse2);
            verifyDiscovery(discovery.ping(serviceName1), pingVerifier, pingResponse1);
            verifyDiscovery(discovery.ping(serviceName2), pingVerifier, pingResponse2);
            verifyDiscovery(discovery.ping(serviceName1, serviceId1), pingVerifier, pingResponse1);
            assertNull(discovery.ping(serviceName1, "badId"));
            assertNull(discovery.ping("bad", "badId"));

            // info discovery
            Verifier infoVerifier = (expected, response) -> {
                assertInstanceOf(InfoResponse.class, response);
                InfoResponse exp = (InfoResponse) expected;
                InfoResponse r = (InfoResponse) response;
                assertEquals(exp.getDescription(), r.getDescription());
                assertEquals(exp.getEndpoints(), r.getEndpoints());
            };
            verifyDiscovery(discovery.info(), infoVerifier, infoResponse1, infoResponse2);
            verifyDiscovery(discovery.info(serviceName1), infoVerifier, infoResponse1);
            verifyDiscovery(discovery.info(serviceName2), infoVerifier, infoResponse2);
            verifyDiscovery(discovery.info(serviceName1, serviceId1), infoVerifier, infoResponse1);
            assertNull(discovery.info(serviceName1, "badId"));
            assertNull(discovery.info("bad", "badId"));

            // stats discovery
            Verifier statsVerifier = (expected, response) -> {
                assertInstanceOf(StatsResponse.class, response);
                StatsResponse exp = (StatsResponse) expected;
                StatsResponse sr = (StatsResponse) response;
                assertEquals(exp.getStarted(), sr.getStarted());
                for (int x = 0; x < 3; x++) {
                    EndpointStats er = exp.getEndpointStatsList().get(x);
                    if (!er.getName().equals(echoEndpointName)) {
                        // echo endpoint has data that will vary
                        assertEquals(er, sr.getEndpointStatsList().get(x));
                    }
                }
            };
            discovery = new Discovery(clientNc); // coverage for the simple constructor
            verifyDiscovery(discovery.stats(), statsVerifier, statsResponse1, statsResponse2);
            verifyDiscovery(discovery.stats(serviceName1), statsVerifier, statsResponse1);
            verifyDiscovery(discovery.stats(serviceName2), statsVerifier, statsResponse2);
            verifyDiscovery(discovery.stats(serviceName1, serviceId1), statsVerifier, statsResponse1);
            assertNull(discovery.stats(serviceName1, "badId"));
            assertNull(discovery.stats("bad", "badId"));

            // ---------------------------------------------------------------------------
            // TEST ADDING AN ENDPOINT TO A RUNNING SERVICE
            // ---------------------------------------------------------------------------
            Endpoint endReverse = Endpoint.builder()
                .name(reverseEndpointName)
                .subject(reverseEndpointSubject)
                .build();

            ServiceEndpoint seRev1 = ServiceEndpoint.builder()
                .endpoint(endReverse)
                .handler(new ReverseHandler(serviceNc1))
                .build();

            service1.addServiceEndpoints(seRev1);
            sleep(100); // give the service some time to get running. remember it's got to subscribe on the server

            for (int x = 0; x < requestCount; x++) {
                verifyServiceExecution(clientNc, reverseEndpointName, reverseEndpointSubject, null, verifyMap);
            }
            infoResponse1 = service1.getInfoResponse();
            boolean found = false;
            for (Endpoint e : infoResponse1.getEndpoints()) {
                if (e.getName().equals(reverseEndpointName)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);

            statsResponse1 = service1.getStatsResponse();
            found = false;
            for (EndpointStats e : statsResponse1.getEndpointStatsList()) {
                if (e.getName().equals(reverseEndpointName)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);

            // test reset: counters are zeroed, start times are not changed
            StatsResponse beforeReset = service1.getStatsResponse();
            Map<String, ZonedDateTime> endpointStarted = new HashMap<>();
            for (EndpointStats es : beforeReset.getEndpointStatsList()) {
                endpointStarted.put(es.getName(), es.getStarted());
            }
            sleep(1);
            service1.reset();
            StatsResponse sr = service1.getStatsResponse();
            assertEquals(beforeReset.getStarted(), sr.getStarted());
            for (int x = 0; x < 3; x++) {
                EndpointStats er = sr.getEndpointStatsList().get(x);
                assertEquals(0, er.getNumRequests());
                assertEquals(0, er.getNumErrors());
                assertEquals(0, er.getProcessingTime());
                assertEquals(0, er.getAverageProcessingTime());
                assertNull(er.getLastError());
                if (er.getName().equals(echoEndpointName)) {
                    assertNotNull(er.getData());
                    assertNotNull(er.getDataAsJson());
                }
                else {
                    assertNull(er.getData());
                    assertNull(er.getDataAsJson());
                }
                assertEquals(endpointStarted.get(er.getName()), er.getStarted());
            }

            // shutdown
            service1.stop();
            serviceStoppedFuture1.get();
            service2.stop(new RuntimeException("Testing stop(Throwable t)"));
            ExecutionException ee = assertThrows(ExecutionException.class, serviceStoppedFuture2::get);
            assertTrue(ee.getMessage().contains("Testing stop(Throwable t)"));
        });
    }

    interface Verifier {
        void verify(ServiceResponse expected, Object response);
    }

    @SuppressWarnings("unchecked")
    private static void verifyDiscovery(Object oResponse, Verifier v, ServiceResponse... expectedResponses) {
        List<Object> responses = oResponse instanceof List ? (List<Object>) oResponse : Collections.singletonList(oResponse);
        assertTrue(responses.size() >= expectedResponses.length);
        for (Object response : responses) {
            ServiceResponse sr = (ServiceResponse) response;
            ServiceResponse exp = find(expectedResponses, sr);
            if (exp != null) {
                // there may be additional response, still not sure why
                assertEquals(exp.getType(), sr.getType());
                assertEquals(exp.getName(), sr.getName());
                assertEquals(exp.getVersion(), sr.getVersion());
                v.verify(exp, response);
            }
        }
    }

    private static ServiceResponse find(ServiceResponse[] expectedResponses, ServiceResponse response) {
        for (ServiceResponse sr : expectedResponses) {
            if (response.id.equals(sr.id)) {
                return sr;
            }
        }
        return null;
    }

    private static void verifyServiceExecution(
        NatsConnection nc, String endpointName, String serviceSubject, Group group, Map<String, String> verifyMap) {
        try {
            String request = Long.toHexString(System.currentTimeMillis()) + Long.toHexString(System.nanoTime()); // just some random text
            String subject = group == null ? serviceSubject : group.getSubject() + DOT + serviceSubject;
            CompletableFuture<Message> future = nc.requestAsync(subject, request.getBytes());
            Message m = future.get();
            String response = new String(m.getData());
            String which = verifyMap.get(endpointName);
            switch (which) {
                case "echoEndpointName":
                    assertEquals(echo(request), response);
                    break;
                case "sortEndpointAscendingName":
                    assertEquals(sortA(request), response);
                    break;
                case "sortEndpointDescendingName":
                    assertEquals(sortD(request), response);
                    break;
                case "reverseEndpointName":
                    assertEquals(reverse(request), response);
                    break;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class EchoHandler implements ServiceMessageHandler {
        NatsConnection conn;
        Function<byte[], String> responder;
        AtomicInteger counter = new AtomicInteger();

        public EchoHandler(NatsConnection conn) {
            this.conn = conn;
            this.responder = d -> {
                counter.incrementAndGet();
                return echo(d);
            };
        }

        public EchoHandler(NatsConnection conn, Function<byte[], String> responder) {
            this.conn = conn;
            this.responder = responder;
        }

        @Override
        public void onMessage(ServiceMessage smsg) {
            counter.incrementAndGet();
            smsg.respond(conn, responder.apply(smsg.getData()));
        }
    }

    static class SortHandlerA extends EchoHandler {
        public SortHandlerA(NatsConnection conn) {
            super(conn, ServiceTests::sortA); // override the response with sortA
        }
    }

    static class SortHandlerD extends EchoHandler {
        public SortHandlerD(NatsConnection conn) {
            super(conn, ServiceTests::sortD); // override the response with sortD
        }
    }

    static class ReverseHandler implements ServiceMessageHandler {
        NatsConnection conn;

        public ReverseHandler(NatsConnection conn) {
            this.conn = conn;
        }

        @Override
        public void onMessage(ServiceMessage smsg) {
            smsg.respond(conn, reverse(smsg.getData()));
        }
    }

    private static String echo(String data) {
        return "Echo " + data;
    }

    private static String echo(byte[] data) {
        return echo(new String(data));
    }

    private static String sortA(byte[] data) {
        Arrays.sort(data);
        return "Sort Ascending " + new String(data);
    }

    private static String sortA(String data) {
        return sortA(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sortD(byte[] data) {
        Arrays.sort(data);
        int len = data.length;
        byte[] descending = new byte[len];
        for (int x = 0; x < len; x++) {
            descending[x] = data[len - x - 1];
        }
        return "Sort Descending " + new String(descending);
    }

    private static String sortD(String data) {
        return sortD(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String reverse(String data) {
        return new StringBuilder(data).reverse().toString();
    }

    private static String reverse(byte[] data) {
        return reverse(new String(data));
    }

    @Test
    public void testIsStartedConfirmsWithTheServer() throws Exception {
        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection anchorNc = SharedServer.sharedConnectionForServer(ts);
            NatsConnection ownNc = SharedServer.connectionForSameServer(anchorNc, optionsBuilder());

            ServiceEndpoint se = ServiceEndpoint.builder()
                .endpoint(Endpoint.builder().name("isstarted").subject(random()).build())
                .handler(new EchoHandler(ownNc))
                .build();

            Service service = new ServiceBuilder()
                .name("IsStarted" + random())
                .version("1.0.0")
                .connection(ownNc)
                .addServiceEndpoint(se)
                .build();

            assertFalse(service.isStarted());
            assertFalse(service.isStarted(1000));

            service.startService();
            assertTrue(service.isStarted());
            assertTrue(service.isStarted(1000));

            // The local flag stays set once startService has been called, so this is the assertion
            // that separates a real readiness check from the old one: with the connection gone there
            // is no way to confirm with the server, and the old isStarted(timeout) still said true.
            ownNc.close();
            assertTrue(service.isStarted());
            assertFalse(service.isStarted(1000));
        });
    }

    @Test
    public void testQueueGroup() throws Exception {
        String serviceName1 = "Service1" + random();
        String serviceName2 = "Service2" + random();

        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection clientNc = SharedServer.sharedConnectionForServer(ts);
            NatsConnection serviceNc1 = SharedServer.sharedConnectionForServer(ts);
            NatsConnection serviceNc2 = SharedServer.sharedConnectionForServer(ts);

            String yesQueueSubject = "subjyes";
            String noQueueSubject = "subjno";

            Endpoint ep1 = Endpoint.builder()
                .name("with")
                .subject(yesQueueSubject)
                .build();

            Endpoint ep2 = Endpoint.builder()
                .name("without")
                .subject(noQueueSubject)
                .noQueueGroup()
                .build();

            EchoHandler handler1Ep1 = new EchoHandler(serviceNc1);
            EchoHandler handler1Ep2 = new EchoHandler(serviceNc1);
            EchoHandler handler2Ep1 = new EchoHandler(serviceNc2);
            EchoHandler handler2Ep2 = new EchoHandler(serviceNc2);

            ServiceEndpoint service1Ep1 = ServiceEndpoint.builder()
                .endpoint(ep1)
                .handler(handler1Ep1)
                .build();

            ServiceEndpoint service1Ep2 = ServiceEndpoint.builder()
                .endpoint(ep2)
                .handler(handler1Ep2)
                .build();

            ServiceEndpoint service2Ep1 = ServiceEndpoint.builder()
                .endpoint(ep1)
                .handler(handler2Ep1)
                .build();

            ServiceEndpoint service2Ep2 = ServiceEndpoint.builder()
                .endpoint(ep2)
                .handler(handler2Ep2)
                .build();

            Service service1 = new ServiceBuilder()
                .name(serviceName1)
                .version("1.0.0")
                .connection(serviceNc1)
                .addServiceEndpoint(service1Ep1)
                .addServiceEndpoint(service1Ep2)
                .build();

            Service service2 = new ServiceBuilder()
                .name(serviceName2)
                .version("1.0.0")
                .connection(serviceNc2)
                .addServiceEndpoint(service2Ep1)
                .addServiceEndpoint(service2Ep2)
                .build();

            service1.startService();
            service2.startService();

            // Gate on the server actually having the endpoint subscriptions. startService only
            // queues the SUBs on the service connections; without this the publishes below can
            // reach the server first and be dropped with no responders, which is what failed on
            // the Windows runner 2026-09-01.
            assertTrue(service1.isStarted(1000));
            assertTrue(service2.isStarted(1000));

            String replyTo = "qreplyto";
            AtomicInteger y1Count = new AtomicInteger();
            AtomicInteger y2Count = new AtomicInteger();
            AtomicInteger n1Count = new AtomicInteger();
            AtomicInteger n2Count = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(6);
            Dispatcher d = clientNc.createDispatcher(m -> {
                switch (new String(m.getData())) {
                    case "Echo y1":
                        y1Count.incrementAndGet();
                        break;
                    case "Echo y2":
                        y2Count.incrementAndGet();
                        break;
                    case "Echo n1":
                        n1Count.incrementAndGet();
                        break;
                    case "Echo n2":
                        n2Count.incrementAndGet();
                        break;
                }
                latch.countDown();
            });
            d.subscribe(replyTo);

            clientNc.publish(yesQueueSubject, replyTo, "y1".getBytes());
            clientNc.publish(yesQueueSubject, replyTo, "y2".getBytes());
            clientNc.publish(noQueueSubject, replyTo, "n1".getBytes());
            clientNc.publish(noQueueSubject, replyTo, "n2".getBytes());

            // Loose on purpose. The latch releases as soon as the six replies land, so a wider
            // window costs nothing when passing and stops a loaded box from failing the test.
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(2, y1Count.get() + y2Count.get());
            assertEquals(4, n1Count.get() + n2Count.get());

            // These services and the dispatcher live on connections shared with the rest of the
            // class, and the subjects here are fixed. Left running, a retry of this test would
            // find the previous attempt's responders still subscribed and double the counts.
            service1.stop();
            service2.stop();
            clientNc.closeDispatcher(d);
        });
    }

    @Test
    public void testResponsesFromAllInstances() throws Exception {
        String serviceName1 = "Service1" + random();
        String serviceName2 = "Service2" + random();

        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection clientNc = SharedServer.sharedConnectionForServer(ts);
            NatsConnection serviceNc1 = SharedServer.sharedConnectionForServer(ts);
            NatsConnection serviceNc2 = SharedServer.sharedConnectionForServer(ts);

            Endpoint ep = Endpoint.builder()
                .name("ep")
                .subject("eps")
                .build();

            EchoHandler handler1 = new EchoHandler(serviceNc1);
            EchoHandler handler2 = new EchoHandler(serviceNc2);

            ServiceEndpoint service1Ep1 = ServiceEndpoint.builder()
                .endpoint(ep)
                .handler(handler1)
                .build();

            ServiceEndpoint service2Ep1 = ServiceEndpoint.builder()
                .endpoint(ep)
                .handler(handler2)
                .build();

            Service service1 = new ServiceBuilder()
                .name(serviceName1)
                .version("1.0.0")
                .connection(serviceNc1)
                .addServiceEndpoint(service1Ep1)
                .build();

            Service service2 = new ServiceBuilder()
                .name(serviceName2)
                .version("1.0.0")
                .connection(serviceNc2)
                .addServiceEndpoint(service2Ep1)
                .build();

            service1.startService();
            service2.startService();

            assertTrue(service1.isStarted(1000));
            assertTrue(service2.isStarted(1000));

            // give 10 seconds for responses, b/c sometimes on GH this takes more than the default 5 seconds.
            // limit to 2 results so we don't wait the entire time when we do get results
            Discovery discovery = new Discovery(clientNc, 10000, 2);

            List<PingResponse> prs = discovery.ping();
            boolean one = false;
            boolean two = false;
            for (PingResponse response : prs) {
                if (response.getName().equals(serviceName1)) {
                    one = true;
                }
                else if (response.getName().equals(serviceName2)) {
                    two = true;
                }
            }
            assertTrue(one);
            assertTrue(two);

            List<InfoResponse> irs = discovery.info();
            one = false;
            two = false;
            for (InfoResponse response : irs) {
                if (response.getName().equals(serviceName1)) {
                    one = true;
                }
                else if (response.getName().equals(serviceName2)) {
                    two = true;
                }
            }
            assertTrue(one);
            assertTrue(two);

            List<StatsResponse> srs = discovery.stats();
            one = false;
            two = false;
            for (StatsResponse response : srs) {
                if (response.getName().equals(serviceName1)) {
                    one = true;
                }
                else if (response.getName().equals(serviceName2)) {
                    two = true;
                }
            }
            assertTrue(one);
            assertTrue(two);
        });
    }

    @Test
    public void testDispatchers() throws Exception {
        runInOwnServer(nc -> {
            Set<Dispatcher> dispatchers = getDispatchers(nc);
            assertEquals(0, dispatchers.size());

            Dispatcher dPing = nc.createDispatcher();
            Dispatcher dInfo = nc.createDispatcher();
            Dispatcher dStats = nc.createDispatcher();
            Dispatcher dEnd = nc.createDispatcher();

            dispatchers = getDispatchers(nc);
            assertEquals(4, dispatchers.size());

            ServiceEndpoint se1 = ServiceEndpoint.builder()
                .endpointName("dispatch")
                .handler(m -> {
                })
                .dispatcher(dEnd)
                .build();
            Service service = new ServiceBuilder()
                .connection(nc)
                .name("testDispatchers")
                .version("0.0.1")
                .addServiceEndpoint(se1)
                .pingDispatcher(dPing)
                .infoDispatcher(dInfo)
                .statsDispatcher(dStats)
                .build();

            CompletableFuture<Boolean> done = service.startService();
            sleep(100); // give the service time to spin up
            service.stop(false); // no need to drain, plus // Coverage
            done.get(100, TimeUnit.MILLISECONDS);

            dispatchers = getDispatchers(nc);
            assertEquals(4, dispatchers.size()); // stop doesn't touch supplied dispatchers

            nc.closeDispatcher(dPing);
            nc.closeDispatcher(dInfo);
            sleep(100); // no rush

            dispatchers = getDispatchers(nc);
            assertEquals(2, dispatchers.size()); // dEnd and dStats
            assertTrue(dispatchers.contains(dStats));
            assertTrue(dispatchers.contains(dEnd));

            service = new ServiceBuilder()
                .connection(nc)
                .name("testDispatchers")
                .version("0.0.1")
                .addServiceEndpoint(se1)
                .statsDispatcher(dStats)
                .build();

            dispatchers = getDispatchers(nc);
            assertEquals(3, dispatchers.size()); // endpoint, stats, internal discovery

            done = service.startService();
            sleep(100); // give the service time to spin up
            service.stop(); // Coverage
            done.get(100, TimeUnit.MILLISECONDS);

            dispatchers = getDispatchers(nc);
            assertEquals(0, dispatchers.size()); // stop() calls drain which closes dispatchers

            se1 = ServiceEndpoint.builder()
                .endpointName("dispatch")
                .handler(m -> {
                })
                .build();

            ServiceEndpoint se2 = ServiceEndpoint.builder()
                .endpointName("another")
                .handler(m -> {
                })
                .build();

            service = new ServiceBuilder()
                .connection(nc)
                .name("testDispatchers")
                .version("0.0.1")
                .addServiceEndpoint(se1)
                .addServiceEndpoint(se2)
                .build();

            dispatchers = getDispatchers(nc);
            assertEquals(2, dispatchers.size()); // 1 internal discovery and 1 internal endpoints

            done = service.startService();
            sleep(100); // give the service time to spin up
            service.stop(); // Coverage
            done.get(100, TimeUnit.MILLISECONDS);

            dispatchers = getDispatchers(nc);
            assertEquals(0, dispatchers.size()); // service cleans up internal dispatchers
        });
    }

    @Test
    public void testServiceBuilderConstruction() {
        Options options = options(); // server not needed, a connection is never made
        NatsConnection conn = new MockNatsNatsConnection(options);
        ServiceEndpoint se = ServiceEndpoint.builder()
            .endpoint(new Endpoint(random()))
            .handler(m -> {})
            .build();

        // minimum valid service
        String name = random();
        Service service = Service.builder().connection(conn).name(name).version("1.0.0").addServiceEndpoint(se).build();
        assertNotNull(service.toString()); // coverage
        assertNotNull(service.getId());
        assertEquals(name, service.getName());
        assertEquals(ServiceBuilder.DEFAULT_DRAIN_TIMEOUT_MILLIS, service.getDrainTimeout());
        assertEquals("1.0.0", service.getVersion());
        assertNull(service.getDescription());
        assertNull(service.getPingResponse().getMetadata());

        // additional fields
        Map<String, String> meta = new HashMap<>();
        meta.put("foo", "bar");
        service = Service.builder().connection(conn).name(name).version("1.0.0").addServiceEndpoint(se)
            .description("desc")
            .drainTimeout(1000)
            .metadata(meta)
            .build();
        assertEquals("desc", service.getDescription());
        assertEquals(1000, service.getDrainTimeout());
        assertTrue(service.getPingResponse().toJson().contains("\"foo\""));
        assertNotNull(service.getPingResponse().getMetadata());

        // metadata is immutable once set: changing the map given to the builder changes nothing
        meta.put("foo", "changed");
        meta.put("added", "value");
        assertEquals(1, service.getPingResponse().getMetadata().size());
        assertEquals("bar", service.getPingResponse().getMetadata().get("foo"));
        assertEquals("bar", service.getInfoResponse().getMetadata().get("foo"));
        assertEquals("bar", service.getStatsResponse().getMetadata().get("foo"));
        assertNull(Service.builder().connection(conn).name(name).version("1.0.0").metadata(null).build().getPingResponse().getMetadata());

        // more coverage
        service = Service.builder().connection(conn).name(name).version("1.0.0").addServiceEndpoint(se)
            .drainTimeout(0)
            .metadata(new HashMap<>())
            .build();
        assertEquals(ServiceBuilder.DEFAULT_DRAIN_TIMEOUT_MILLIS, service.getDrainTimeout());

        service = Service.builder().connection(conn).name(name).version("1.0.0").addServiceEndpoint(se)
            .drainTimeout(1000)
            .build();
        assertEquals(1000, service.getDrainTimeout());

        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(null));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(""));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_PRINTABLE));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_DOT));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(STAR_NOT_SEGMENT)); // invalid in the middle
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(GT_NOT_SEGMENT)); // invalid in the middle
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_DOLLAR));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_LOW));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_127));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_FWD_SLASH));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_BACK_SLASH));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_EQUALS));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_TIC));

        assertThrows(IllegalArgumentException.class, () -> Service.builder().version(null));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().version(""));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().version("not-semver"));

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
            () -> Service.builder().name(name).version("1.0.0").addServiceEndpoint(se).build());
        assertTrue(iae.getMessage().contains("NatsConnection cannot be null"));

        iae = assertThrows(IllegalArgumentException.class,
            () -> Service.builder().connection(conn).version("1.0.0").addServiceEndpoint(se).build());
        assertTrue(iae.getMessage().contains("Name cannot be null or empty"));

        iae = assertThrows(IllegalArgumentException.class,
            () -> Service.builder().connection(conn).name(name).addServiceEndpoint(se).build());
        assertTrue(iae.getMessage().contains("Version cannot be null or empty"));

        assertDoesNotThrow(
            () -> Service.builder().connection(conn).name(name).version("1.0.0").build());
    }

    @Test
    public void testAddingEndpointAfterServiceBuilderConstruction() {
        Options options = options(); // server not needed, a connection is never made
        NatsConnection conn = new MockNatsNatsConnection(options);
        ServiceEndpoint se = ServiceEndpoint.builder()
                .endpoint(new Endpoint(random()))
                .handler(m -> {
                })
                .build();

        // minimum valid service
        String name = random();
        Service service = Service.builder().connection(conn).name(name).version("1.0.0").addServiceEndpoint(se).build();
        assertNotNull(service.toString()); // coverage
        assertNotNull(service.getId());
        assertEquals(name, service.getName());
        assertEquals(ServiceBuilder.DEFAULT_DRAIN_TIMEOUT_MILLIS, service.getDrainTimeout());
        assertEquals("1.0.0", service.getVersion());
        assertNull(service.getDescription());

        service = Service.builder().connection(conn).name(name).version("1.0.0")
                .description("desc")
                .drainTimeout(1000)
                .build();

        service.addServiceEndpoints(se);
        assertEquals("desc", service.getDescription());
        assertEquals(1000, service.getDrainTimeout());

        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(null));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(""));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_PRINTABLE));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_DOT));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(STAR_NOT_SEGMENT)); // invalid in the middle
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(GT_NOT_SEGMENT)); // invalid in the middle
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_DOLLAR));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_LOW));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_127));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_FWD_SLASH));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_BACK_SLASH));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_EQUALS));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().name(HAS_TIC));

        assertThrows(IllegalArgumentException.class, () -> Service.builder().version(null));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().version(""));
        assertThrows(IllegalArgumentException.class, () -> Service.builder().version("not-semver"));

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
                () -> Service.builder().name(name).version("1.0.0").addServiceEndpoint(se).build());
        assertTrue(iae.getMessage().contains("NatsConnection cannot be null"));

        iae = assertThrows(IllegalArgumentException.class,
                () -> Service.builder().connection(conn).version("1.0.0").addServiceEndpoint(se).build());
        assertTrue(iae.getMessage().contains("Name cannot be null or empty"));

        iae = assertThrows(IllegalArgumentException.class,
                () -> Service.builder().connection(conn).name(name).addServiceEndpoint(se).build());
        assertTrue(iae.getMessage().contains("Version cannot be null or empty"));

        assertDoesNotThrow(() -> Service.builder().connection(conn).name(name).version("1.0.0").build());
    }

    @Test
    public void testServiceBuilderPrefix() {
        Options options = options(); // server not needed, a connection is never made
        NatsConnection conn = new MockNatsNatsConnection(options);
        String name = random();
        Supplier<ServiceBuilder> sb = () -> Service.builder().connection(conn).name(name).version("1.0.0");

        // default and normalized
        assertEquals("$SRV", sb.get().build().getPrefix());
        assertEquals("$SRV", sb.get().prefix(null).build().getPrefix());
        assertEquals("$SRV", sb.get().prefix("").build().getPrefix());
        assertEquals("$SRV", sb.get().prefix(" ").build().getPrefix());
        assertEquals("$SRV", sb.get().prefix(".").build().getPrefix());
        assertEquals("$SRV", sb.get().prefix("$SRV").build().getPrefix());
        assertEquals("$SRV", sb.get().prefix("$SRV.").build().getPrefix());
        assertEquals("ACME", sb.get().prefix("ACME").build().getPrefix());
        assertEquals("ACME", sb.get().prefix(" ACME. ").build().getPrefix());
        assertEquals("ACME.SRV", sb.get().prefix("ACME.SRV").build().getPrefix());
        assertEquals("AcMe", sb.get().prefix("AcMe").build().getPrefix());
        assertEquals("acme", sb.get().prefix("acme").build().getPrefix());
        assertEquals("$srv", sb.get().prefix("$srv").build().getPrefix()); // $SRV is case-sensitive
        assertEquals("ACME.$srv", sb.get().prefix("ACME.$srv").build().getPrefix());

        // refused
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("A..B"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("ACME.."));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix(".ACME"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("A B"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("A\tB"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("A\u0007B"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("*"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix(">"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("A.*"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("A.>"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("A*"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("$SRV.ACME"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("ACME.$SRV"));
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("A.$SRV.B"));

        // an endpoint cannot be the prefix or under it, nor under $SRV
        String epName = random();
        Function<String, ServiceEndpoint> se = subject -> ServiceEndpoint.builder()
            .endpoint(Endpoint.builder().name(epName).subject(subject).build())
            .handler(m -> {})
            .build();
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("ACME").addServiceEndpoint(se.apply("ACME")).build());
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("ACME").addServiceEndpoint(se.apply("ACME.PING")).build());
        assertThrows(IllegalArgumentException.class, () -> sb.get().prefix("ACME.SRV").addServiceEndpoint(se.apply("ACME.SRV.foo")).build());
        assertThrows(IllegalArgumentException.class, () -> sb.get().addServiceEndpoint(se.apply("$SRV.foo")).build());
        assertThrows(IllegalArgumentException.class, () -> se.apply("$SRV.foo")); // $SRV is refused by the endpoint builder for any prefix
        assertDoesNotThrow(() -> sb.get().prefix("ACME").addServiceEndpoint(se.apply("ACMEX.foo")).build());
        assertDoesNotThrow(() -> sb.get().prefix("ACME").addServiceEndpoint(se.apply("acme.foo")).build());
        assertDoesNotThrow(() -> sb.get().prefix("ACME.SRV").addServiceEndpoint(se.apply("ACME.foo")).build());

        // the same check when adding after build, and a refused endpoint means none are added
        Service service = sb.get().prefix("ACME").build();
        assertThrows(IllegalArgumentException.class, () -> service.addServiceEndpoints(se.apply("ACME.PING")));
        ServiceEndpoint ok = ServiceEndpoint.builder()
            .endpoint(Endpoint.builder().name("ok").subject("ok").build())
            .handler(m -> {})
            .build();
        assertThrows(IllegalArgumentException.class, () -> service.addServiceEndpoints(ok, se.apply("ACME.INFO")));
        assertNull(service.getEndpointStats("ok"));
        assertThrows(IllegalArgumentException.class, () -> service.addServiceEndpoints(Collections.singletonList(se.apply("ACME"))));
        service.addServiceEndpoints(ok, null); // a null service endpoint is skipped
        assertNotNull(service.getEndpointStats("ok"));
    }

    @Test
    public void testServicePrefix() throws Exception {
        // its own server, since the default $SRV discovery must find no services
        runInOwnServer(nc -> {
            for (String prefix : new String[]{"ACME", "ACME.SRV", "AcMe", "acme"}) {
                ServiceEndpoint se = ServiceEndpoint.builder()
                    .endpointName("add")
                    .handler(m -> m.respond(nc, "added"))
                    .build();

                Service service = new ServiceBuilder()
                    .connection(nc)
                    .name("orders")
                    .version("1.0.0")
                    .prefix(prefix)
                    .addServiceEndpoint(se)
                    .build();
                service.startService();
                assertTrue(service.isStarted(1000));
                String id = service.getId();

                // all nine discovery subjects are under the prefix, exactly as cased
                for (String verb : new String[]{"PING", "INFO", "STATS"}) {
                    for (String subject : new String[]{prefix + DOT + verb, prefix + DOT + verb + ".orders", prefix + DOT + verb + ".orders." + id}) {
                        Message m = nc.request(subject, null, 2000);
                        assertNotNull(m, subject);
                        assertFalse(m.isStatusMessage(), subject);
                        assertTrue(new String(m.getData(), StandardCharsets.UTF_8).contains(id), subject);
                    }
                }

                // discovery with the prefix finds the service, the default does not
                Discovery discovery = new Discovery(nc, prefix);
                assertEquals(1, discovery.ping().size());
                assertEquals(1, discovery.info("orders").size());
                assertEquals(1, discovery.stats("orders").size());
                assertNotNull(discovery.ping("orders", id));
                assertNotNull(new Discovery(nc, 500, 5, " " + prefix + ". ").ping("orders", id));

                Discovery defaultDiscovery = new Discovery(nc, 500, 5);
                assertEquals(0, defaultDiscovery.ping().size());
                assertNull(defaultDiscovery.ping("orders", id));

                service.stop();
            }
        });
    }

    @Test
    public void testHandlerException() throws Exception {
        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection nc = SharedServer.sharedConnectionForServer(ts);
            ServiceEndpoint exServiceEndpoint = ServiceEndpoint.builder()
                .endpointName("exEndpoint")
                .endpointSubject("exSubject")
                .handler(m -> {
                    throw new RuntimeException("handler-problem");
                })
                .build();

            Service exService = new ServiceBuilder()
                .connection(nc)
                .name("ExceptionService")
                .version("0.0.1")
                .addServiceEndpoint(exServiceEndpoint)
                .build();
            exService.startService();

            CompletableFuture<Message> future = nc.requestAsync("exSubject", null);
            Message m = future.get();
            assertEquals("java.lang.RuntimeException: handler-problem", m.getHeaders().getFirst(NATS_SERVICE_ERROR));
            assertEquals("500", m.getHeaders().getFirst(NATS_SERVICE_ERROR_CODE));
            StatsResponse sr = exService.getStatsResponse();
            EndpointStats er = sr.getEndpointStatsList().get(0);
            assertEquals(1, er.getNumRequests());
            assertEquals(1, er.getNumErrors());
            assertEquals("java.lang.RuntimeException: handler-problem", er.getLastError());
        });
    }

    @Test
    public void testHandlerRespondError() throws Exception {
        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection nc = SharedServer.sharedConnectionForServer(ts);
            ServiceEndpoint seEndpoint = ServiceEndpoint.builder()
                .endpointName("seEndpoint")
                .endpointSubject("seSubject")
                .handler(m -> {
                    if (new String(m.getData()).equals("fail")) {
                        m.respondError(nc, 400, "failed");
                    }
                    else {
                        m.respond(nc, "ok");
                    }
                })
                .build();

            Service seService = new ServiceBuilder()
                .connection(nc)
                .name("RespondErrorHandlerService")
                .version("0.0.1")
                .addServiceEndpoint(seEndpoint)
                .build();
            seService.startService();

            assertEquals("ok", new String(nc.requestAsync("seSubject", "pass".getBytes()).get().getData()));
            Message m = nc.requestAsync("seSubject", "fail".getBytes()).get();
            assertEquals("failed", m.getHeaders().getFirst(NATS_SERVICE_ERROR));
            assertEquals("400", m.getHeaders().getFirst(NATS_SERVICE_ERROR_CODE));

            // the error is counted when the handler returns, which can be just after the reply arrives
            EndpointStats er = seService.getEndpointStats("seEndpoint");
            for (int i = 0; i < 100 && er.getNumErrors() == 0; i++) {
                Thread.sleep(10);
                er = seService.getEndpointStats("seEndpoint");
            }
            assertEquals(2, er.getNumRequests());
            assertEquals(1, er.getNumErrors());
            assertEquals("failed", er.getLastError());

            seService.reset();
            er = seService.getEndpointStats("seEndpoint");
            assertEquals(0, er.getNumErrors());
            assertNull(er.getLastError());

            seService.stop();
        });
    }

    @Test
    public void testRespondError() throws Exception {
        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection nc = SharedServer.sharedConnectionForServer(ts);
            AtomicReference<ServiceMessage> lastMsg = new AtomicReference<>();
            Headers customHeaders = new Headers().put("X-Order", "7");
            byte[] bytes = "payload".getBytes();
            JsonValue json = new JsonValue("payload");
            ServiceEndpoint seEndpoint = ServiceEndpoint.builder()
                .endpointName("reEndpoint")
                .endpointSubject("reSubject")
                .handler(m -> {
                    switch (new String(m.getData())) {
                        case "plain": m.respondError(nc, 400, "bad order"); break;
                        case "empty": m.respondError(nc, 500, ""); break;
                        case "headers": m.respondError(nc, 1000, "headers", customHeaders); break;
                        case "bytes": m.respondError(nc, 1001, "bytes", bytes); break;
                        case "string": m.respondError(nc, 1002, "string", "payload"); break;
                        case "json": m.respondError(nc, 1003, "json", json); break;
                        case "bytesHeaders": m.respondError(nc, 1004, "bytesHeaders", bytes, customHeaders); break;
                        case "stringHeaders": m.respondError(nc, 1005, "stringHeaders", "payload", customHeaders); break;
                        case "jsonHeaders": m.respondError(nc, 1006, "jsonHeaders", json, customHeaders); break;
                        case "nullDescription": m.respondError(nc, 405, (String) null); break;
                        case "nullString": m.respond(nc, (String) null); break;
                        case "nullJson": m.respond(nc, (JsonSerializable) null); break;
                        case "nullCode": m.respondError(nc, null, "no code"); break;
                        case "nulls": m.respondError(nc, null, "nulls", (byte[]) null, null); break;
                        case "nullRespond": m.respond(nc, (byte[]) null, null); break;
                        default: m.respond(nc, "ok");
                    }
                    lastMsg.set(m);
                })
                .build();

            Service reService = new ServiceBuilder()
                .connection(nc)
                .name("RespondErrorService")
                .version("0.0.1")
                .addServiceEndpoint(seEndpoint)
                .build();
            reService.startService();

            // code and description, no body
            Message m = nc.requestAsync("reSubject", "plain".getBytes()).get();
            assertErrorReply(m, "bad order", "400", "", false);
            assertLastError(reService, 1, "bad order");
            assertEquals("bad order", lastMsg.get().getResponseErrorDescription());
            assertEquals(400, lastMsg.get().getResponseErrorCode());
            assertNull(lastMsg.get().getResponseData());

            // an empty description is sent as given
            m = nc.requestAsync("reSubject", "empty".getBytes()).get();
            assertErrorReply(m, "", "500", "", false);
            assertLastError(reService, 2, "");

            // custom headers, no data
            m = nc.requestAsync("reSubject", "headers".getBytes()).get();
            assertErrorReply(m, "headers", "1000", "", true);
            assertLastError(reService, 3, "headers");
            assertNull(lastMsg.get().getResponseData());

            // data, each type
            m = nc.requestAsync("reSubject", "bytes".getBytes()).get();
            assertErrorReply(m, "bytes", "1001", "payload", false);
            assertLastError(reService, 4, "bytes");
            assertEquals("payload", new String(lastMsg.get().getResponseData()));

            m = nc.requestAsync("reSubject", "string".getBytes()).get();
            assertErrorReply(m, "string", "1002", "payload", false);
            assertLastError(reService, 5, "string");

            m = nc.requestAsync("reSubject", "json".getBytes()).get();
            assertErrorReply(m, "json", "1003", "\"payload\"", false);
            assertLastError(reService, 6, "json");

            // data and custom headers, each type
            m = nc.requestAsync("reSubject", "bytesHeaders".getBytes()).get();
            assertErrorReply(m, "bytesHeaders", "1004", "payload", true);
            assertLastError(reService, 7, "bytesHeaders");
            assertEquals("7", lastMsg.get().getResponseHeaders().getFirst("X-Order"));
            assertEquals("1004", lastMsg.get().getResponseHeaders().getFirst(NATS_SERVICE_ERROR_CODE));

            m = nc.requestAsync("reSubject", "stringHeaders".getBytes()).get();
            assertErrorReply(m, "stringHeaders", "1005", "payload", true);
            assertLastError(reService, 8, "stringHeaders");

            m = nc.requestAsync("reSubject", "jsonHeaders".getBytes()).get();
            assertErrorReply(m, "jsonHeaders", "1006", "\"payload\"", true);
            assertLastError(reService, 9, "jsonHeaders");

            // the error headers were added to a copy of the custom headers
            assertNull(customHeaders.getFirst(NATS_SERVICE_ERROR));
            assertNull(customHeaders.getFirst(NATS_SERVICE_ERROR_CODE));

            // a null description sends only the code, which becomes the last error
            m = nc.requestAsync("reSubject", "nullDescription".getBytes()).get();
            assertNull(m.getHeaders().getFirst(NATS_SERVICE_ERROR));
            assertEquals("405", m.getHeaders().getFirst(NATS_SERVICE_ERROR_CODE));
            assertLastError(reService, 10, "405");
            assertNull(lastMsg.get().getResponseErrorDescription());
            assertEquals(405, lastMsg.get().getResponseErrorCode());

            // null data is sent as an empty body
            m = nc.requestAsync("reSubject", "nullString".getBytes()).get();
            assertEquals(0, m.getData().length);
            waitForRequests(reService, 11);
            assertNull(lastMsg.get().getResponseData());

            m = nc.requestAsync("reSubject", "nullJson".getBytes()).get();
            assertEquals(0, m.getData().length);
            waitForRequests(reService, 12);
            assertNull(lastMsg.get().getResponseData());

            // a null code sends only the description
            m = nc.requestAsync("reSubject", "nullCode".getBytes()).get();
            assertEquals("no code", m.getHeaders().getFirst(NATS_SERVICE_ERROR));
            assertNull(m.getHeaders().getFirst(NATS_SERVICE_ERROR_CODE));
            assertLastError(reService, 11, "no code");
            assertEquals("no code", lastMsg.get().getResponseErrorDescription());
            assertNull(lastMsg.get().getResponseErrorCode());

            // a null code, data and custom headers
            m = nc.requestAsync("reSubject", "nulls".getBytes()).get();
            assertEquals("nulls", m.getHeaders().getFirst(NATS_SERVICE_ERROR));
            assertNull(m.getHeaders().getFirst(NATS_SERVICE_ERROR_CODE));
            assertEquals(0, m.getData().length);
            assertLastError(reService, 12, "nulls");
            assertNull(lastMsg.get().getResponseData());
            assertNull(lastMsg.get().getResponseHeaders().getFirst("X-Order"));

            // null data and null custom headers, not an error
            m = nc.requestAsync("reSubject", "nullRespond".getBytes()).get();
            assertFalse(m.hasHeaders());
            assertEquals(0, m.getData().length);
            waitForRequests(reService, 15);
            assertNull(lastMsg.get().getResponseData());
            assertNull(lastMsg.get().getResponseHeaders());

            assertEquals("ok", new String(nc.requestAsync("reSubject", "pass".getBytes()).get().getData()));
            waitForRequests(reService, 16);
            assertEquals(12, reService.getEndpointStats("reEndpoint").getNumErrors());
            assertEquals("nulls", reService.getEndpointStats("reEndpoint").getLastError());
            assertNull(lastMsg.get().getResponseErrorDescription());
            assertNull(lastMsg.get().getResponseErrorCode());
            assertEquals("ok", new String(lastMsg.get().getResponseData()));
            assertNull(lastMsg.get().getResponseHeaders());

            reService.stop();
        });
    }

    @Test
    public void testServiceReplyMessage() {
        // no headers, no data
        ServiceReplyMessage t = new ServiceReplyMessage(NatsMessage.builder().subject("sub").build());
        assertEquals("sub", t.getSubject());
        assertNull(t.getHeaders());
        assertNotNull(t.getData());
        assertEquals(0, t.getData().length);
        assertFalse(t.isError());
        assertNull(t.getErrorDescription());
        assertNull(t.getErrorCode());

        // data, headers that are not error headers
        t = new ServiceReplyMessage(NatsMessage.builder().subject("sub").data("payload")
            .headers(new Headers().put("X-Order", "7")).build());
        assertEquals("payload", new String(t.getData(), StandardCharsets.UTF_8));
        assertNotNull(t.getHeaders());
        assertEquals("7", t.getHeaders().getFirst("X-Order"));
        assertFalse(t.isError());
        assertNull(t.getErrorDescription());
        assertNull(t.getErrorCode());

        // both error headers
        t = new ServiceReplyMessage(NatsMessage.builder().subject("sub")
            .headers(new Headers().put(NATS_SERVICE_ERROR, "bad order").put(NATS_SERVICE_ERROR_CODE, "400")).build());
        assertTrue(t.isError());
        assertEquals("bad order", t.getErrorDescription());
        assertEquals(400, t.getErrorCode());

        // code only
        t = new ServiceReplyMessage(NatsMessage.builder().subject("sub")
            .headers(new Headers().put(NATS_SERVICE_ERROR_CODE, "503")).build());
        assertTrue(t.isError());
        assertNull(t.getErrorDescription());
        assertEquals(503, t.getErrorCode());

        // description only
        t = new ServiceReplyMessage(NatsMessage.builder().subject("sub")
            .headers(new Headers().put(NATS_SERVICE_ERROR, "no code")).build());
        assertTrue(t.isError());
        assertEquals("no code", t.getErrorDescription());
        assertNull(t.getErrorCode());

        // an empty description only is not an error
        t = new ServiceReplyMessage(NatsMessage.builder().subject("sub")
            .headers(new Headers().put(NATS_SERVICE_ERROR, "")).build());
        assertFalse(t.isError());
        assertNull(t.getErrorDescription());
        assertNull(t.getErrorCode());
    }

    private static void assertErrorReply(Message m, String description, String code, String data, boolean customHeader) {
        assertEquals(description, m.getHeaders().getFirst(NATS_SERVICE_ERROR));
        assertEquals(code, m.getHeaders().getFirst(NATS_SERVICE_ERROR_CODE));
        assertEquals(data, new String(m.getData()));
        assertEquals(customHeader ? "7" : null, m.getHeaders().getFirst("X-Order"));
    }

    // the handler sets lastMsg before it returns, which can be just after the reply arrives
    private static void waitForRequests(Service service, long numRequests) throws InterruptedException {
        for (int i = 0; i < 100 && service.getEndpointStats("reEndpoint").getNumRequests() < numRequests; i++) {
            Thread.sleep(10);
        }
        assertEquals(numRequests, service.getEndpointStats("reEndpoint").getNumRequests());
    }

    // the error is counted when the handler returns, which can be just after the reply arrives
    private static void assertLastError(Service service, long numErrors, String lastError) throws InterruptedException {
        EndpointStats es = service.getEndpointStats("reEndpoint");
        for (int i = 0; i < 100 && es.getNumErrors() < numErrors; i++) {
            Thread.sleep(10);
            es = service.getEndpointStats("reEndpoint");
        }
        assertEquals(numErrors, es.getNumErrors());
        assertEquals(lastError, es.getLastError());
    }

    @Test
    public void testServiceMessage() throws Exception {
        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection nc = SharedServer.sharedConnectionForServer(ts);
            AtomicInteger which = new AtomicInteger();
            ServiceEndpoint se = ServiceEndpoint.builder()
                .endpointName("testServiceMessage")
                .handler(m -> {
                    assertNotNull(m.getReplyTo());
                    // Coverage // just hitting all the reply variations
                    switch (which.incrementAndGet()) {
                        case 1:
                            m.respond(nc, "1".getBytes());
                            break;
                        case 2:
                            m.respond(nc, "2");
                            break;
                        case 3:
                            m.respond(nc, new JsonValue("3"));
                            break;
                        case 4:
                            m.respond(nc, "4".getBytes(), m.getHeaders());
                            break;
                        case 5:
                            m.respond(nc, "5", m.getHeaders());
                            break;
                        case 6:
                            m.respond(nc, new JsonValue("6"), m.getHeaders());
                            break;
                        case 7:
                            // Coverage, Message Interface
                            assertEquals("testServiceMessage", m.getSubject());
                            assertFalse(m.hasHeaders());
                            assertNull(m.getHeaders());
                            // the actual reply
                            m.respondError(nc, 500, "error");
                            break;
                    }
                })
                .build();

            Service service = new ServiceBuilder()
                .connection(nc)
                .name("testService")
                .version("0.0.1")
                .addServiceEndpoint(se)
                .build();
            service.startService();

            CompletableFuture<Message> future = nc.requestAsync("testServiceMessage", null);
            Message m = future.get();
            assertEquals("1", new String(m.getData()));
            assertFalse(m.hasHeaders());

            future = nc.requestAsync("testServiceMessage", null);
            m = future.get();
            assertEquals("2", new String(m.getData()));
            assertFalse(m.hasHeaders());

            future = nc.requestAsync("testServiceMessage", null);
            m = future.get();
            assertEquals("\"3\"", new String(m.getData()));
            assertFalse(m.hasHeaders());

            Headers h = new Headers().put("h", "4");
            future = nc.request(NatsMessage.builder().subject("testServiceMessage").headers(h).build());
            m = future.get();
            assertEquals("4", new String(m.getData()));
            assertTrue(m.hasHeaders());
            assertEquals("4", m.getHeaders().getFirst("h"));

            h = new Headers().put("h", "5");
            future = nc.request(NatsMessage.builder().subject("testServiceMessage").headers(h).build());
            m = future.get();
            assertEquals("5", new String(m.getData()));
            assertTrue(m.hasHeaders());
            assertEquals("5", m.getHeaders().getFirst("h"));

            h = new Headers().put("h", "6");
            future = nc.request(NatsMessage.builder().subject("testServiceMessage").headers(h).build());
            m = future.get();
            assertEquals("\"6\"", new String(m.getData()));
            assertTrue(m.hasHeaders());
            assertEquals("6", m.getHeaders().getFirst("h"));

            future = nc.requestAsync("testServiceMessage", null);
            m = future.get();
            assertEquals(0, m.getData().length);
            assertTrue(m.hasHeaders());
            assertEquals("error", m.getHeaders().getFirst(NATS_SERVICE_ERROR));
            assertEquals("500", m.getHeaders().getFirst(NATS_SERVICE_ERROR_CODE));
        });
    }

    @Test
    public void testEndpointConstruction() {
        EqualsVerifier.simple().forClass(Endpoint.class).verify();
        Map<String, String> metadata = new HashMap<>();

        String name = random();
        String subject = random();

        Endpoint e = new Endpoint(name);
        assertEpNameSubQ(e, name, name);
        assertEquals(e, Endpoint.builder().endpoint(e).build());
        assertNull(e.getMetadata());
        String jep = e.toJson();
        String sep = e.toString();
        assertTrue(sep.contains(jep));

        e = new Endpoint(name, metadata);
        assertEpNameSubQ(e, name, name);
        assertEquals(e, Endpoint.builder().endpoint(e).build());
        assertNull(e.getMetadata());

        e = new Endpoint(name, subject);
        assertEpNameSubQ(e, name, subject);
        assertEquals(e, Endpoint.builder().endpoint(e).build());
        assertEquals(Endpoint.DEFAULT_QGROUP, e.getQueueGroup());

        e = Endpoint.builder()
            .name(name).subject(subject)
            .build();
        assertEpNameSubQ(e, name, subject);
        assertEquals(e, Endpoint.builder().endpoint(e).build());

        e = Endpoint.builder()
            .name(name).subject(subject)
            .metadata(metadata)
            .queueGroup(null) // coverage
            .build();
        assertEpNameSubQ(e, name, subject);
        assertNull(e.getMetadata());
        assertEquals(Endpoint.DEFAULT_QGROUP, e.getQueueGroup());

        e = Endpoint.builder()
            .name(name).subject(subject)
            .metadata(metadata)
            .queueGroup("qg") // coverage
            .build();
        assertNull(e.getMetadata());
        assertEquals("qg", e.getQueueGroup());

        // an empty or blank queue group means no queue group, and the queue group is trimmed
        assertNull(new Endpoint(name, subject, "", null).getQueueGroup());
        assertNull(new Endpoint(name, subject, " ", null).getQueueGroup());
        assertNull(Endpoint.builder().name(name).queueGroup("").build().getQueueGroup());
        assertEquals("qg", new Endpoint(name, subject, " qg ", null).getQueueGroup());
        assertNull(new Endpoint(JsonParser.parseUnchecked("{\"name\":\"" + name + "\",\"queue_group\":\"\"}")).getQueueGroup());

        // a queue group is an ADR-6 queue-name, so it cannot hold '.', '*' or '>'
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, subject, "q.dot", null));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, subject, "q*", null));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, subject, "q>", null));
        assertThrows(IllegalArgumentException.class, () -> Endpoint.builder().name(name).queueGroup("q.dot").build());
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(JsonParser.parseUnchecked("{\"name\":\"" + name + "\",\"queue_group\":\"q.dot\"}")));

        metadata.put("k", "v");

        e = new Endpoint(name, subject, metadata);
        assertEpNameSubQ(e, name, subject);
        assertEquals(metadata, e.getMetadata());

        e = Endpoint.builder()
            .name(name).subject(subject)
            .metadata(metadata)
            .build();
        assertEpNameSubQ(e, name, subject);
        assertEquals(metadata, e.getMetadata());

        // internal allows null queue group
        e = new Endpoint(name, subject, null, metadata, false);
        assertNull(e.getQueueGroup());

        // some subject testing
        e = new Endpoint(name, "foo.>");
        assertEquals("foo.>", e.getSubject());
        e = new Endpoint(name, "foo.*");
        assertEquals("foo.*", e.getSubject());

        // an empty subject means the name is the subject
        assertEpNameSubQ(new Endpoint(name, ""), name, name);
        assertEpNameSubQ(Endpoint.builder().name(name).subject("").build(), name, name);

        // from json, the name is required and a null or empty subject means the name is the subject
        assertEquals(subject, new Endpoint(JsonParser.parseUnchecked("{\"name\":\"" + name + "\",\"subject\":\"" + subject + "\"}")).getSubject());
        assertEquals(name, new Endpoint(JsonParser.parseUnchecked("{\"name\":\"" + name + "\"}")).getSubject());
        assertEquals(name, new Endpoint(JsonParser.parseUnchecked("{\"name\":\"" + name + "\",\"subject\":\"\"}")).getSubject());
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(JsonParser.parseUnchecked("{\"subject\":\"" + subject + "\"}")));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(JsonParser.parseUnchecked("{\"name\":\"\",\"subject\":\"" + subject + "\"}")));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(JsonParser.parseUnchecked("{\"name\":\"" + HAS_SPACE + "\",\"subject\":\"" + subject + "\"}")));

        // coverage
        e = new Endpoint(name, subject, metadata);
        assertEpNameSubQ(e, name, subject);
        assertEquals(metadata, e.getMetadata());
        assertThrows(IllegalArgumentException.class, () -> Endpoint.builder().build());

        // many names are bad and is required
        assertThrows(IllegalArgumentException.class, () -> new Endpoint((String) null));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(""));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_PRINTABLE));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_DOT));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(STAR_NOT_SEGMENT)); // invalid in the middle
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(GT_NOT_SEGMENT)); // invalid in the middle
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_DOLLAR));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_LOW));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_127));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_FWD_SLASH));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_BACK_SLASH));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_EQUALS));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(HAS_TIC));

        // typical subjects are bad
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, HAS_CR));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, HAS_LF));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, STAR_NOT_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, GT_NOT_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(name, STARTS_WITH_DOT));
    }

    private static void assertEpNameSubQ(Endpoint ep, String name, String exSubject) {
        assertEquals(name, ep.getName());
        assertEquals(exSubject, ep.getSubject());
        assertEquals(Endpoint.DEFAULT_QGROUP, ep.getQueueGroup());
    }

    @Test
    public void testEndpointResponseConstruction() {
        JsonValue data = new JsonValue("data");
        EqualsVerifier.simple().forClass(EndpointStats.class)
            .withPrefabValues(JsonValue.class, data, JsonValue.NULL)
            .verify();
        ZonedDateTime zdt = DateTimeUtils.utcNow();

        EndpointStats er = new EndpointStats("name", "subject", "queue", 0, 0, 0, null, null, zdt);
        assertEquals("name", er.getName());
        assertEquals("subject", er.getSubject());
        assertEquals("queue", er.getQueueGroup());
        assertNull(er.getLastError());
        assertNull(er.getData());
        assertEquals(0, er.getNumRequests());
        assertEquals(0, er.getNumErrors());
        assertEquals(0, er.getProcessingTime());
        assertEquals(0, er.getAverageProcessingTime());
        assertEquals(zdt, er.getStarted());

        er = new EndpointStats("name", "subject", "queue", 2, 4, 10, "lastError", data, zdt);
        assertEquals("name", er.getName());
        assertEquals("subject", er.getSubject());
        assertEquals("queue", er.getQueueGroup());
        assertEquals("lastError", er.getLastError());
        assertEquals("\"data\"", er.getData().toString());
        assertEquals(2, er.getNumRequests());
        assertEquals(4, er.getNumErrors());
        assertEquals(10, er.getProcessingTime());
        assertEquals(5, er.getAverageProcessingTime());
        assertEquals(zdt, er.getStarted());

        String j = er.toJson();
        assertTrue(j.startsWith("{"));
        assertTrue(j.contains("\"name\":\"name\""));
        assertTrue(j.contains("\"subject\":\"subject\""));
        assertTrue(j.contains("\"queue_group\":\"queue\""));
        assertTrue(j.contains("\"last_error\":\"lastError\""));
        assertTrue(j.contains("\"data\":\"data\""));
        assertTrue(j.contains("\"num_requests\":2"));
        assertTrue(j.contains("\"num_errors\":4"));
        assertTrue(j.contains("\"processing_time\":10"));
        assertTrue(j.contains("\"average_processing_time\":5"));
        assertEquals(toKey(EndpointStats.class) + j, er.toString());
    }

    @Test
    public void testGroupConstruction() {
        String subject1 = random();
        String subject2 = random();
        String subject3 = random();
        Group g1 = new Group(subject1);
        Group g2 = new Group(subject2);
        Group g3 = new Group(subject3);
        assertEquals(subject1, g1.getName());
        assertEquals(subject1, g1.getSubject());
        assertEquals(subject2, g2.getName());
        assertEquals(subject2, g2.getSubject());
        assertEquals(subject3, g3.getName());
        assertEquals(subject3, g3.getSubject());
        assertNull(g1.getNext());
        assertNull(g2.getNext());
        assertNull(g3.getNext());
        assertTrue(g1.toString().contains(subject1)); // coverage
        assertTrue(g2.toString().contains(subject2)); // coverage
        assertTrue(g3.toString().contains(subject3)); // coverage

        assertEquals(g1, g1.appendGroup(g2));
        assertEquals(subject2, g1.getNext().getName());
        assertNull(g2.getNext());
        assertEquals(subject1, g1.getName());
        assertEquals(subject1 + DOT + subject2, g1.getSubject());
        assertEquals(subject2, g2.getName());
        assertEquals(subject2, g2.getSubject());
        assertTrue(g1.toString().contains(subject2)); // coverage

        assertEquals(g1, g1.appendGroup(g3));
        assertEquals(subject2, g1.getNext().getName());
        assertEquals(subject3, g1.getNext().getNext().getName());
        assertEquals(subject1, g1.getName());
        assertEquals(subject1 + DOT + subject2 + DOT + subject3, g1.getSubject());
        assertTrue(g1.toString().contains(subject3)); // coverage

        g1 = new Group("foo.*");
        assertEquals("foo.*", g1.getName());

        // null, empty and blank names are allowed, stored as null and add nothing to the subject
        assertNull(new Group(null).getName());
        assertNull(new Group("").getName());
        assertNull(new Group(" ").getName());
        assertNull(new Group(null).getSubject());
        assertEquals("Group []", new Group(null).toString());
        assertEquals("foo", new Group(" foo ").getName());
        assertEquals(subject1, new Group(null).appendGroup(new Group(subject1)).getSubject());
        assertEquals(subject1, new Group(subject1).appendGroup(new Group("")).getSubject());
        assertEquals(subject1 + DOT + subject2, new Group(subject1).appendGroup(new Group("")).appendGroup(new Group(subject2)).getSubject());
        assertNull(new Group("").appendGroup(new Group(null)).getSubject());

        assertThrows(IllegalArgumentException.class, () -> new Group(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> new Group(STAR_NOT_SEGMENT)); // invalid in the middle
        assertThrows(IllegalArgumentException.class, () -> new Group("foo.>")); // GT invalid everywhere
        assertThrows(IllegalArgumentException.class, () -> new Group(GT_NOT_SEGMENT)); // GT invalid everywhere

        EqualsVerifier.simple().forClass(Group.class).withPrefabValues(Group.class, g1, g2).verify();
    }

    @Test
    public void testServiceEndpointConstruction() {
        String subject1 = random();
        String subject2 = random();
        Group g1 = new Group(subject1);
        Group g2 = new Group(subject2).appendGroup(g1);
        Endpoint e1 = new Endpoint(random(), random());
        Endpoint e2 = new Endpoint(random(), random());
        ServiceMessageHandler smh = m -> {};
        Supplier<JsonValue> sds = () -> null;

        ServiceEndpoint se = ServiceEndpoint.builder()
            .endpoint(e1)
            .handler(smh)
            .statsDataSupplier(sds)
            .dispatcher(null) // just for some coverage, dispatcher is tested elsewhere
            .build();
        assertNull(se.getGroup());
        assertEquals(e1, se.getEndpoint());
        assertEquals(e1.getName(), se.getName());
        assertEquals(e1.getSubject(), se.getSubject());
        assertEquals(smh, se.getHandler());
        assertEquals(sds, se.getStatsDataSupplier());
        assertNull(se.getDispatcher());
        assertNull(se.getGroupName());

        se = ServiceEndpoint.builder()
            .group(g1)
            .endpoint(e1)
            .handler(smh)
            .build();
        assertEquals(g1, se.getGroup());
        assertEquals(e1, se.getEndpoint());
        assertEquals(e1.getName(), se.getName());
        assertEquals(se.getGroup().getName(), se.getGroupName());
        assertEquals(g1.getSubject() + DOT + e1.getSubject(), se.getSubject());
        assertNull(se.getStatsDataSupplier());
        assertNull(se.getDispatcher());

        se = ServiceEndpoint.builder()
            .group(g2)
            .endpoint(e1)
            .handler(smh)
            .build();
        assertEquals(g2, se.getGroup());
        assertEquals(e1, se.getEndpoint());
        assertEquals(e1.getName(), se.getName());
        assertEquals(g2.getSubject() + DOT + e1.getSubject(), se.getSubject());

        // a group with no name adds no prefix
        se = ServiceEndpoint.builder()
            .group(new Group(""))
            .endpoint(e1)
            .handler(smh)
            .build();
        assertNull(se.getGroupName());
        assertEquals(e1.getSubject(), se.getSubject());

        se = ServiceEndpoint.builder()
            .endpoint(e1)
            .endpoint(e2) // last one wins
            .handler(smh)
            .build();
        assertEquals(e2, se.getEndpoint());
        assertEquals(e2.getName(), se.getName());
        assertEquals(e2.getSubject(), se.getSubject());

        se = ServiceEndpoint.builder()
            .endpoint(e1)
            .endpointName(e2.getName())
            .handler(smh)
            .build();
        assertEquals(e2.getName(), se.getName());
        assertEquals(e1.getSubject(), se.getSubject());

        se = ServiceEndpoint.builder()
            .endpoint(e1)
            .endpointSubject(e2.getSubject())
            .handler(smh)
            .build();
        assertEquals(e1.getName(), se.getName());
        assertEquals(e2.getSubject(), se.getSubject());

        Map<String, String> metadata = new HashMap<>();
        se = ServiceEndpoint.builder()
            .endpoint(e1)
            .endpointMetadata(metadata)
            .handler(smh)
            .build();
        assertNull(se.getMetadata());

        metadata.put("k", "v");
        se = ServiceEndpoint.builder()
            .endpoint(e1)
            .endpointMetadata(metadata)
            .handler(smh)
            .build();
        assertTrue(Objects.equals(metadata, se.getMetadata()));

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
            () -> ServiceEndpoint.builder().build());
        assertTrue(iae.getMessage().contains("Endpoint"));

        iae = assertThrows(IllegalArgumentException.class,
            () -> ServiceEndpoint.builder().endpoint(e1).build());
        assertTrue(iae.getMessage().contains("Handler"));

        // $SRV is reserved for service discovery, with or without a group
        for (String reserved : new String[]{"$SRV", "$SRV.custom", "$SRV.PING.orders"}) {
            iae = assertThrows(IllegalArgumentException.class,
                () -> ServiceEndpoint.builder().endpointName(random()).endpointSubject(reserved).handler(smh).build());
            assertTrue(iae.getMessage().contains("$SRV"));
        }
        iae = assertThrows(IllegalArgumentException.class,
            () -> ServiceEndpoint.builder().group(new Group("$SRV")).endpoint(e1).handler(smh).build());
        assertTrue(iae.getMessage().contains("$SRV"));

        // only the exact $SRV token is reserved
        assertEquals("$SRVX.custom", ServiceEndpoint.builder().endpointName(random()).endpointSubject("$SRVX.custom").handler(smh).build().getSubject());
        assertEquals("custom.$SRV", ServiceEndpoint.builder().endpointName(random()).endpointSubject("custom.$SRV").handler(smh).build().getSubject());

        se = ServiceEndpoint.builder()
            .endpointName("directName")
            .endpointQueueGroup("directQ")
            .handler(m -> {})
            .build();

        assertEquals("directName", se.getName());
        assertEquals("directName", se.getSubject());
        assertEquals("directQ", se.getQueueGroup());

        se = ServiceEndpoint.builder()
            .endpointName("directName")
            .endpointSubject("directSubject")
            .endpointQueueGroup("directQ")
            .handler(m -> {})
            .build();

        assertEquals("directName", se.getName());
        assertEquals("directSubject", se.getSubject());
        assertEquals("directQ", se.getQueueGroup());

        Group g = new Group("directG");
        se = ServiceEndpoint.builder()
            .group(g)
            .endpointName("directName")
            .endpointSubject("directSubject")
            .endpointQueueGroup("directQ")
            .handler(m -> {})
            .build();

        assertEquals("directName", se.getName());
        assertEquals("directG.directSubject", se.getSubject());
        assertEquals("directQ", se.getQueueGroup());
    }

    @Test
    public void testUtilToDiscoverySubject() {
        assertEquals("$SRV.PING", Service.toDiscoverySubject("$SRV", SRV_PING, null, null));
        assertEquals("$SRV.PING.myservice", Service.toDiscoverySubject("$SRV", SRV_PING, "myservice", null));
        assertEquals("$SRV.PING.myservice.123", Service.toDiscoverySubject("$SRV", SRV_PING, "myservice", "123"));
        assertEquals("ACME.SRV.PING.myservice.123", Service.toDiscoverySubject("ACME.SRV", SRV_PING, "myservice", "123"));
    }

    static class TestServiceResponses extends ServiceResponse {
        static String TYPE = "io.nats.micro.v1.test_response";

        public TestServiceResponses(byte[] jsonBytes) {
            this(parseMessage(jsonBytes));
        }

        public TestServiceResponses(JsonValue jv) {
            super(TYPE, jv);
        }
    }

    @Test
    public void testServiceResponsesConstruction() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("k", "v");

        PingResponse pr1 = new PingResponse("id", "name", "0.0.0", metadata);
        PingResponse pr2 = new PingResponse(pr1.toJson().getBytes());
        validateApiInOutPingResponse(pr1);
        validateApiInOutPingResponse(pr2);
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> new TestServiceResponses(pr1.toJsonValue()));
        assertTrue(iae.getMessage().contains("Invalid type"));

        iae = assertThrows(IllegalArgumentException.class, () -> new TestServiceResponses("{[bad json".getBytes()));
        assertTrue(iae.getMessage().contains("Type cannot be null"));

        String json1 = "{\"id\":\"id\",\"name\":\"name\",\"version\":\"0.0.0\"}";
        iae = assertThrows(IllegalArgumentException.class, () -> new TestServiceResponses(json1.getBytes()));
        assertTrue(iae.getMessage().contains("Type cannot be null"));

        String json2 = "{\"name\":\"name\",\"version\":\"0.0.0\",\"type\":\"io.nats.micro.v1.test_response\"}";
        iae = assertThrows(IllegalArgumentException.class, () -> new TestServiceResponses(json2.getBytes()));
        assertTrue(iae.getMessage().contains("Id cannot be null"));

        String json3 = "{\"id\":\"id\",\"version\":\"0.0.0\",\"type\":\"io.nats.micro.v1.test_response\"}";
        iae = assertThrows(IllegalArgumentException.class, () -> new TestServiceResponses(json3.getBytes()));
        assertTrue(iae.getMessage().contains("Name cannot be null"));

        String json4 = "{\"id\":\"id\",\"name\":\"name\",\"type\":\"io.nats.micro.v1.test_response\"}";
        iae = assertThrows(IllegalArgumentException.class, () -> new TestServiceResponses(json4.getBytes()));
        assertTrue(iae.getMessage().contains("Version cannot be null"));

        Map<String, String> endMeta = new HashMap<>();
        endMeta.put("foo", "bar");
        Endpoint ep = new Endpoint("endfoo", endMeta);
        ServiceEndpoint se = new ServiceEndpoint(ep, m -> {}, null);
        InfoResponse ir1 = new InfoResponse("id", "name", "0.0.0", metadata, "desc");
        ir1.addServiceEndpoint(se);
        InfoResponse ir2 = new InfoResponse(ir1.toJson().getBytes());
        validateApiInOutInfoResponse(ir1);
        validateApiInOutInfoResponse(ir2);

        ZonedDateTime serviceStarted = DateTimeUtils.utcNow();
        ZonedDateTime[] endStarteds = new ZonedDateTime[2];
        sleep(100);
        endStarteds[0] = DateTimeUtils.utcNow();
        sleep(100);
        endStarteds[1] = DateTimeUtils.utcNow();

        List<EndpointStats> statsList = new ArrayList<>();
        JsonValue[] data = new JsonValue[]{supplyData(), supplyData()};
        statsList.add(new EndpointStats("endName0", "endSubject0", "endQueue0", 1000, 0, 10000, "lastError0", data[0], endStarteds[0]));
        statsList.add(new EndpointStats("endName1", "endSubject1", "endQueue1", 2000, 10, 10000, "lastError1", data[1], endStarteds[1]));

        StatsResponse stat1 = new StatsResponse(pr1, serviceStarted, statsList);
        StatsResponse stat2 = new StatsResponse(stat1.toJson().getBytes());
        validateApiInOutStatsResponse(stat1, serviceStarted, endStarteds, data);
        validateApiInOutStatsResponse(stat2, serviceStarted, endStarteds, data);

        EqualsVerifier.simple().forClass(PingResponse.class).withIgnoredFields("serialized").verify();
        EqualsVerifier.simple().forClass(InfoResponse.class).withIgnoredFields("serialized").verify();
        EqualsVerifier.simple().forClass(StatsResponse.class)
            .withPrefabValues(EndpointStats.class, statsList.get(0), statsList.get(1))
            .withIgnoredFields("serialized")
            .verify();
    }

    private static void validateApiInOutStatsResponse(StatsResponse stat, ZonedDateTime serviceStarted, ZonedDateTime[] endStarteds, JsonValue[] data) {
        validateApiInOutServiceResponse(stat, StatsResponse.TYPE);
        assertEquals(serviceStarted, stat.getStarted());
        assertEquals(2, stat.getEndpointStatsList().size());
        for (int x = 0; x < 2; x++) {
            EndpointStats e = stat.getEndpointStatsList().get(x);
            assertEquals("endName" + x, e.getName());
            assertEquals("endSubject" + x, e.getSubject());
            assertEquals("endQueue" + x, e.getQueueGroup());
            long nr = x * 1000 + 1000;
            long errs = x * 10;
            long avg = 10000 / nr;
            assertEquals(nr, e.getNumRequests());
            assertEquals(errs, e.getNumErrors());
            assertEquals(10000, e.getProcessingTime());
            assertEquals(avg, e.getAverageProcessingTime());
            assertEquals("lastError" + x, e.getLastError());
            assertEquals(new TestStatsData(data[x]), new TestStatsData(e.getData()));
            assertEquals(endStarteds[x], e.getStarted());
        }
    }

    private static void validateApiInOutInfoResponse(InfoResponse r) {
        validateApiInOutServiceResponse(r, InfoResponse.TYPE);
        assertEquals("desc", r.getDescription());
        assertEquals(1, r.getEndpoints().size());
        Endpoint endpoint = r.getEndpoints().get(0);
        assertEquals("endfoo", endpoint.getName());
        assertEquals("bar", endpoint.getMetadata().get("foo"));
    }

    private static void validateApiInOutPingResponse(PingResponse r) {
        validateApiInOutServiceResponse(r, PingResponse.TYPE);
    }

    private static void validateApiInOutServiceResponse(ServiceResponse r, String type) {
        assertEquals(type, r.getType());
        assertEquals("id", r.getId());
        assertEquals("name", r.getName());
        assertEquals("0.0.0", r.getVersion());
        assertNotNull(r.getMetadata());
        assertEquals(1, r.getMetadata().size());
        assertEquals("v", r.getMetadata().get("k"));
        assertNull(r.getMetadata().get("x"));
        String j = r.toJson();
        assertTrue(j.startsWith("{"));
        assertTrue(j.contains("\"type\":\"" + type + "\""));
        assertTrue(j.contains("\"name\":\"name\""));
        assertTrue(j.contains("\"id\":\"id\""));
        assertTrue(j.contains("\"version\":\"0.0.0\""));
        assertTrue(j.contains("\"metadata\":{\"k\":\"v\"}"));
        assertEquals(toKey(r.getClass()) + j, r.toString());
    }

    private static int _dataX = -1;

    public static JsonValue supplyData() {
        _dataX++;
        return new TestStatsData("s-" + _dataX, _dataX).toJsonValue();
    }

    static class TestStatsData implements JsonSerializable {
        public String sData;
        public int iData;

        public TestStatsData(String sData, int iData) {
            this.sData = sData;
            this.iData = iData;
        }

        public TestStatsData(JsonValue jv) {
            sData = readString(jv, "sdata");
            iData = readInteger(jv, "idata", -1);
        }

        @Override
        @NonNull
        public String toJson() {
            return toKey(getClass()) + toJsonValue().toJson();
        }

        @Override
        @NonNull
        public JsonValue toJsonValue() {
            Map<String, JsonValue> map = new HashMap<>();
            map.put("sdata", new JsonValue(sData));
            map.put("idata", new JsonValue(iData));
            return new JsonValue(map);
        }

        @Override
        public String toString() {
            return toJsonValue().toString(getClass());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestStatsData that = (TestStatsData) o;

            if (iData != that.iData) return false;
            return Objects.equals(sData, that.sData);
        }

        @Override
        public int hashCode() {
            int result = sData != null ? sData.hashCode() : 0;
            result = 31 * result + iData;
            return result;
        }
    }

    static class TestInboxSupplier implements Supplier<String> {
        boolean wasCalled = false;
        @Override
        public String get() {
            wasCalled = true;
            return "CUSTOM_INBOX";
        }
    }

    @Test
    public void testInboxSupplier() throws Exception {
        runInSharedNamed(SERVICE_TESTS_SHARED_NAME, ts -> {
            NatsConnection nc = SharedServer.sharedConnectionForServer(ts);
            Discovery discovery = new Discovery(nc, 100, 1);
            TestInboxSupplier supplier = new TestInboxSupplier();
            discovery.setInboxSupplier(supplier);
            // no service is running, so this is the no responders path - it returns empty
            // instead of throwing, which is what the discoverMany status guard is for
            assertTrue(discovery.ping("servicename").isEmpty());
            assertTrue(supplier.wasCalled);
        });
    }
}
