package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.RateLimitProperties;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.config.SecurityConfig;
import com.darshangohil.urlshortener.web.security.LoginThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthCheckController.class)
@EnableConfigurationProperties(RateLimitProperties.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, LoginThrottle.class})
class HealthCheckControllerWebTest {

    @Autowired MockMvc mockMvc;

    @Test
    void pingAnswersWithPlainTextWithoutSigningIn() throws Exception {
        // it used to return the text as a view name, which was a 500
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("URL Shortener Service is up and running"));
    }
}
