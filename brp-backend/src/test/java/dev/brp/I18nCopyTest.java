package dev.brp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The copy is a feature: dark humor resolves by Accept-Language, the error
 * code stays machine-stable. Business behavior unchanged; only the words judge.
 */
@SpringBootTest
@AutoConfigureMockMvc
class I18nCopyTest {

    @Autowired MockMvc mvc;

    @Test
    void englishMemo() throws Exception {
        mvc.perform(get("/api/v1/tps-reports/weekly").param("withCoverSheet", "false")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "TPS reports now require a cover sheet. Didn't you get the memo? Everyone got the memo."));
    }

    @Test
    void chineseMemo() throws Exception {
        mvc.perform(get("/api/v1/tps-reports/weekly").param("withCoverSheet", "false")
                        .header("Accept-Language", "zh-CN")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "TPS 报告现在必须附封面页。备忘录没发到您那里吗？所有人都收到了。"));
    }
}
