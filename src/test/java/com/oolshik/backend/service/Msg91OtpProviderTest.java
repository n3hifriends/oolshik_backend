package com.oolshik.backend.service;

import com.oolshik.backend.config.OtpProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class Msg91OtpProviderTest {

    private MockRestServiceServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.verify();
        }
    }

    @Test
    void sendsOtpToConfiguredMsg91Endpoint() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://msg91.test/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authkey", "test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("9876543210")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your Oolshik login code")))
                .andRespond(withSuccess("{\"type\":\"success\"}", MediaType.APPLICATION_JSON));

        Msg91OtpProvider provider = new Msg91OtpProvider(restTemplate, properties("https://msg91.test/send"));

        provider.sendOtp("+919876543210", "Your Oolshik login code: 123456");
    }

    @Test
    void throwsWhenMsg91ReturnsFailure() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://msg91.test/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        Msg91OtpProvider provider = new Msg91OtpProvider(restTemplate, properties("https://msg91.test/send"));

        assertThatThrownBy(() -> provider.sendOtp("+919876543210", "test message"))
                .isInstanceOf(OtpDeliveryException.class)
                .hasMessageContaining("MSG91");
    }

    @Test
    void requiresSenderIdAndEntityId() {
        OtpProperties missingSender = properties("https://msg91.test/send");
        missingSender.getMsg91().setSenderId(null);

        assertThatThrownBy(() -> new Msg91OtpProvider(new RestTemplate(), missingSender))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_OTP_MSG91_SENDER_ID");

        OtpProperties missingEntity = properties("https://msg91.test/send");
        missingEntity.getMsg91().setEntityId(null);

        assertThatThrownBy(() -> new Msg91OtpProvider(new RestTemplate(), missingEntity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_OTP_MSG91_ENTITY_ID");
    }

    private OtpProperties properties(String baseUrl) {
        OtpProperties properties = new OtpProperties();
        properties.setProvider("msg91");
        properties.getMsg91().setBaseUrl(baseUrl);
        properties.getMsg91().setApiKey("test-key");
        properties.getMsg91().setTemplateId("template-id");
        properties.getMsg91().setSenderId("OOLSK");
        properties.getMsg91().setEntityId("entity-id");
        return properties;
    }
}
