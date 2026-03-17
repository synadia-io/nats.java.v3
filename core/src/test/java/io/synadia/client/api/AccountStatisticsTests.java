package io.synadia.client.api;

import io.synadia.client.impl.JetStreamTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.synadia.client.support.JsonUtils.EMPTY_JSON;
import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static org.junit.jupiter.api.Assertions.*;

public class AccountStatisticsTests extends JetStreamTestBase {

    @Test
    public void testAccountStatsImpl() {
        String json = dataAsString("AccountStatistics.json");
        AccountStatistics as = new AccountStatistics(getDataMessage(json));
        assertEquals(101, as.getMemory());
        assertEquals(102, as.getStorage());
        assertEquals(103, as.getStreams());
        assertEquals(104, as.getConsumers());
        assertEquals(105, as.getReservedMemory());
        assertEquals(106, as.getReservedStorage());
        validateAccountLimits(as.getLimits(), 200);

        assertEquals("ngs", as.getDomain());

        ApiStats api = as.getApi();
        assertEquals(301, api.getTotalApiRequests());
        assertEquals(302, api.getErrorCount());
        assertEquals(303, api.getLevel());
        assertEquals(304, api.getInFlight());

        Map<String, AccountTier> tiers = as.getTiers();
        validateTier(tiers.get("R1"), 400, 500);
        validateTier(tiers.get("R3"), 600, 700);

        assertNotNull(as.toString()); // COVERAGE

        as = new AccountStatistics(getDataMessage(EMPTY_JSON));
        assertEquals(0, as.getMemory());
        assertEquals(0, as.getStorage());
        assertEquals(0, as.getStreams());
        assertEquals(0, as.getConsumers());

        AccountLimits al = as.getLimits();
        assertEquals(0, al.getMaxMemory());
        assertEquals(0, al.getMaxStorage());
        assertEquals(0, al.getMaxStreams());
        assertEquals(0, al.getMaxConsumers());
        assertEquals(0, al.getMaxAckPending());
        assertEquals(0, al.getMemoryMaxStreamBytes());
        assertEquals(0, al.getStorageMaxStreamBytes());
        assertFalse(al.isMaxBytesRequired());

        api = as.getApi();
        assertNotNull(api);
        assertEquals(0, api.getTotalApiRequests());
        assertEquals(0, api.getErrorCount());
        assertEquals(0, api.getLevel());
        assertEquals(0, api.getInFlight());
    }

    private void validateTier(AccountTier tier, int tierBase, int limitsIdBase) {
        assertNotNull(tier);
        assertEquals(tierBase + 1, tier.getMemoryBytes());
        assertEquals(tierBase + 2, tier.getStorageBytes());
        assertEquals(tierBase + 3, tier.getStreams());
        assertEquals(tierBase + 4, tier.getConsumers());
        assertEquals(tierBase + 5, tier.getReservedMemory());
        assertEquals(tierBase + 6, tier.getReservedStorage());
        validateAccountLimits(tier.getLimits(), limitsIdBase);
    }

    private static void validateAccountLimits(AccountLimits al, int limitsIdBase) {
        assertNotNull(al);
        assertEquals(limitsIdBase + 1, al.getMaxMemory());
        assertEquals(limitsIdBase + 2, al.getMaxStorage());
        assertEquals(limitsIdBase + 3, al.getMaxStreams());
        assertEquals(limitsIdBase + 4, al.getMaxConsumers());
        assertEquals(limitsIdBase + 5, al.getMaxAckPending());
        assertEquals(limitsIdBase + 6, al.getMemoryMaxStreamBytes());
        assertEquals(limitsIdBase + 7, al.getStorageMaxStreamBytes());
        assertTrue(al.isMaxBytesRequired());
    }
}
