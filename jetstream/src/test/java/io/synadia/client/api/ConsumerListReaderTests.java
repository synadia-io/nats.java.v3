package io.synadia.client.api;

import io.synadia.client.impl.JetStreamApiException;
import io.synadia.client.impl.TestableConsumerListReader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static io.synadia.client.utils.TestBase.getDataMessage;
import static org.junit.jupiter.api.Assertions.*;

public class ConsumerListReaderTests {

    // ====================================================================================================
    // Single-page response: the fixture has 2 consumers and total/offset/limit such that
    // total (2) <= lastOffset (42) + limit (256), so hasMore() must be false after one process().
    // ====================================================================================================
    @Test
    public void testConsumerListSinglePage() throws Exception {
        String json = dataAsString("ConsumerListResponse.json");

        TestableConsumerListReader clr = new TestableConsumerListReader();
        // before any process, the engine is the empty one: total=Integer.MAX_VALUE so hasMore is true
        assertTrue(clr.hasMore());
        // initial nextJson is the first request with offset 0
        byte[] firstReq = clr.nextJson();
        assertNotNull(firstReq);
        assertEquals("{\"offset\":0}", new String(firstReq, StandardCharsets.UTF_8));

        clr.process(getDataMessage(json));

        List<ConsumerInfo> consumers = clr.getConsumers();
        assertEquals(2, consumers.size());

        // total (2) <= offset(42)+limit(256), so no more pages
        assertFalse(clr.hasMore());
        assertNull(clr.nextJson());

        // sanity check on the items themselves so we know the fixture really parsed
        assertEquals("cname1", consumers.get(0).getName());
        assertEquals("cname2", consumers.get(1).getName());

        assertNotNull(consumers.get(0).toString()); // COVERAGE
    }

    // ====================================================================================================
    // Empty body: no consumers array at all, no total/offset/limit fields.
    // total defaults to -1, so hasMore() is false (Integer.MAX_VALUE -> -1 after reading).
    // ====================================================================================================
    @Test
    public void testConsumerListEmptyBody() throws Exception {
        TestableConsumerListReader clr = new TestableConsumerListReader();
        clr.process(getDataMessage("{}"));
        assertEquals(0, clr.getConsumers().size());
        // total read as -1 (default in ListRequestEngine ctor), nextOffset=0, so -1 > 0 is false
        assertFalse(clr.hasMore());
        assertNull(clr.nextJson());
    }

    // ====================================================================================================
    // Multi-page paging: feed ListResponsePage1.json then ListResponsePage2.json.
    // These fixtures have total/offset/limit set up for paging but no "consumers" array,
    // so item accumulation is 0 per page; the focus is on engine pagination state.
    // ====================================================================================================
    @Test
    public void testConsumerListMultiPagePaging() throws Exception {
        String page1Json = dataAsString("ListResponsePage1.json");
        String page2Json = dataAsString("ListResponsePage2.json");

        TestableConsumerListReader clr = new TestableConsumerListReader();

        // ---- page 1 ----
        // page1: total=15, offset=0, limit=10, no consumers array
        clr.process(getDataMessage(page1Json));

        // accumulated items reflect page 1 (the fixture has no consumers array, so 0)
        assertEquals(0, clr.getConsumers().size());

        // there are 15 total and we've reached offset 0+10=10, so more pages exist
        assertTrue(clr.hasMore());

        // nextJson must reference the next offset (10) and be non-empty
        byte[] next = clr.nextJson();
        assertNotNull(next);
        assertTrue(next.length > 0);
        String nextStr = new String(next, StandardCharsets.UTF_8);
        assertEquals("{\"offset\":10}", nextStr);
        assertTrue(nextStr.contains("\"offset\":10"));

        // ---- page 2 ----
        // page2: total=15, offset=10, limit=10, no consumers array
        clr.process(getDataMessage(page2Json));

        // accumulated items reflect both pages (still 0 since neither fixture has a consumers array)
        assertEquals(0, clr.getConsumers().size());

        // 15 total, nextOffset=10+10=20, so no more pages
        assertFalse(clr.hasMore());
        assertNull(clr.nextJson());
    }

    // ====================================================================================================
    // Multi-page paging with actual items in each page: shows the items accumulate across process() calls.
    // ====================================================================================================
    @Test
    public void testConsumerListMultiPagePagingWithItems() throws Exception {
        // craft two pages that DO carry consumers arrays, mirroring server paging behavior
        String page1 = "{"
                + "\"type\":\"io.nats.jetstream.api.v1.consumer_list_response\","
                + "\"total\":3,\"offset\":0,\"limit\":2,"
                + "\"consumers\":["
                + "  {\"stream_name\":\"s\",\"name\":\"c1\",\"created\":\"2021-01-20T23:41:08.579594Z\"},"
                + "  {\"stream_name\":\"s\",\"name\":\"c2\",\"created\":\"2021-01-20T23:41:08.579594Z\"}"
                + "]}";
        String page2 = "{"
                + "\"type\":\"io.nats.jetstream.api.v1.consumer_list_response\","
                + "\"total\":3,\"offset\":2,\"limit\":2,"
                + "\"consumers\":["
                + "  {\"stream_name\":\"s\",\"name\":\"c3\",\"created\":\"2021-01-20T23:41:08.579594Z\"}"
                + "]}";

        TestableConsumerListReader clr = new TestableConsumerListReader();

        clr.process(getDataMessage(page1));
        assertEquals(2, clr.getConsumers().size());
        assertTrue(clr.hasMore());
        assertEquals("{\"offset\":2}", new String(clr.nextJson(), StandardCharsets.UTF_8));

        clr.process(getDataMessage(page2));
        assertEquals(3, clr.getConsumers().size());
        assertFalse(clr.hasMore());
        assertNull(clr.nextJson());

        assertEquals("c1", clr.getConsumers().get(0).getName());
        assertEquals("c2", clr.getConsumers().get(1).getName());
        assertEquals("c3", clr.getConsumers().get(2).getName());
    }

    // ====================================================================================================
    // Filter-not-supported path: ConsumerListReader uses the no-filter ctor of AbstractListReader,
    // so calling nextJson(filter) must throw IllegalArgumentException.
    // ====================================================================================================
    @Test
    public void testNextJsonWithFilterNotSupported() {
        TestableConsumerListReader clr = new TestableConsumerListReader();
        assertThrows(IllegalArgumentException.class, () -> clr.nextJson("anything"));
        assertThrows(IllegalArgumentException.class, () -> clr.nextJson(null));
    }

    // ====================================================================================================
    // Error path: a server response carrying an Error must surface as JetStreamApiException
    // from ListRequestEngine's ctor (called inside process()).
    // ====================================================================================================
    @Test
    public void testProcessThrowsOnErrorResponse() {
        String json = dataAsString("GenericErrorResponse.json");
        TestableConsumerListReader clr = new TestableConsumerListReader();
        JetStreamApiException ex = assertThrows(JetStreamApiException.class,
                () -> clr.process(getDataMessage(json)));
        assertNotNull(ex.getMessage()); // COVERAGE
    }
}
