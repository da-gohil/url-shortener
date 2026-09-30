package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.UrlSafetyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SafeBrowsingClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer google = MockRestServiceServer.bindTo(builder).build();

    private SafeBrowsingClient client(String apiKey) {
        return new SafeBrowsingClient(builder, new UrlSafetyProperties(List.of(), apiKey));
    }

    @Test
    void aMatchMeansFlagged() {
        google.expect(requestTo(SafeBrowsingClient.ENDPOINT + "?key=test-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"url\":\"https://malware.example/\"")))
                .andExpect(content().string(containsString("SOCIAL_ENGINEERING")))
                .andRespond(withSuccess("{\"matches\":[{\"threatType\":\"MALWARE\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client("test-key").isFlagged("https://malware.example/")).isTrue();
        google.verify();
    }

    @Test
    void anEmptyResponseMeansClean() {
        google.expect(requestTo(SafeBrowsingClient.ENDPOINT + "?key=test-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client("test-key").isFlagged("https://example.com/")).isFalse();
    }

    @Test
    void anOutageAtGoogleLetsTheLinkThrough() {
        google.expect(requestTo(SafeBrowsingClient.ENDPOINT + "?key=test-key"))
                .andRespond(withServerError());

        assertThat(client("test-key").isFlagged("https://example.com/")).isFalse();
    }

    @Test
    void withoutAKeyNothingIsSent() {
        assertThat(client("").isFlagged("https://example.com/")).isFalse();
        google.verify();   // no requests were expected, and none were made
    }
}
