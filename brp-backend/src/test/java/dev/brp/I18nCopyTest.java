package dev.brp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The copy is a feature: dark humor resolves by Accept-Language, the error
 * code stays machine-stable. Business behavior unchanged; only the words judge.
 *
 * Since audit #28 the suite also guards the whole bundle, not one lucky key:
 * every English key must exist in Chinese and vice versa, and no entry may
 * ship blank — a half-translated surface is a bug users can read.
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

    @Test
    void englishAndChineseCarryIdenticalKeySets() throws Exception {
        Set<String> en = bundle("messages.properties").stringPropertyNames();
        Set<String> zh = bundle("messages_zh.properties").stringPropertyNames();
        assertFalse(en.isEmpty(), "the English bundle is the contract; empty means broken classpath");
        Set<String> missingInZh = en.stream().filter(k -> !zh.contains(k)).collect(Collectors.toSet());
        Set<String> missingInEn = zh.stream().filter(k -> !en.contains(k)).collect(Collectors.toSet());
        assertEquals(Set.of(), missingInZh, "keys missing from the Chinese bundle");
        assertEquals(Set.of(), missingInEn, "keys missing from the English bundle");
    }

    @Test
    void noBundleEntryShipsBlank() throws Exception {
        Properties en = bundle("messages.properties");
        Properties zh = bundle("messages_zh.properties");
        for (String key : en.stringPropertyNames()) {
            assertTrue(!en.getProperty(key).isBlank(), "blank English copy: " + key);
        }
        for (String key : zh.stringPropertyNames()) {
            assertTrue(!zh.getProperty(key).isBlank(), "blank Chinese copy: " + key);
        }
    }

    /** Whole-bundle loader: message interpolation braces survive untouched. */
    private static Properties bundle(String name) throws IOException {
        try (InputStream in = I18nCopyTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("bundle not on the classpath: " + name);
            }
            Properties props = new Properties();
            props.load(in);
            return props;
        }
    }
}
