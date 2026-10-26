package dev.brp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Economy + quota console pages: numbers from the ledger, licenses from the desk. */
@SpringBootTest
@AutoConfigureMockMvc
class PortalConsoleTest {

    @Autowired MockMvc mvc;
    @Autowired dev.brp.service.EconomyReportService economy;
    @Autowired dev.brp.service.CurrencyService currencies;

    @Test
    void economyPageShowsThePrinterLogAndRegistry() throws Exception {
        economy.materializeToday("BRC");   // seed today's row so history exists
        mvc.perform(MockMvcRequestBuilders.get("/portal/economy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("money printer")))
                .andExpect(content().string(containsString("Better Resource Coin")))
                .andExpect(content().string(containsString("legal tender")));
    }

    @Test
    void quotaPageSetsLimitsAndListsBuckets() throws Exception {
        String tenant = "qc-" + java.util.UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(MockMvcRequestBuilders.post("/portal/quotas/" + tenant + "/machine_slot")
                        .param("limit", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/portal/quotas"));

        mvc.perform(MockMvcRequestBuilders.get("/portal/quotas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(tenant)))
                .andExpect(content().string(containsString("Chunk gluttony")));
    }
}
