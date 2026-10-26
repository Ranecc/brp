package dev.brp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The console renders: classic admin chrome, creeper accents, real numbers. */
@SpringBootTest
@AutoConfigureMockMvc
class PortalSmokeTest {

    @Autowired MockMvc mvc;

    @Test
    void dashboardRendersWithEnterpriseChrome() throws Exception {
        mvc.perform(get("/portal"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("货币总量 M")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("TPS")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("cover sheet")));
    }

    @Test
    void approvalsInboxRenders() throws Exception {
        mvc.perform(get("/portal/approvals"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("corporate labyrinth")));
    }
}
