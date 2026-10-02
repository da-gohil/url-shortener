package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.RateLimitProperties;
import com.darshangohil.urlshortener.config.MethodSecurityConfig;
import com.darshangohil.urlshortener.web.security.LoginThrottle;
import com.darshangohil.urlshortener.config.SecurityConfig;
import com.darshangohil.urlshortener.domain.exception.EmailAlreadyExistsException;
import com.darshangohil.urlshortener.domain.models.CreateUserCmd;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.domain.services.PasswordPolicy;
import com.darshangohil.urlshortener.domain.services.UserService;
import com.darshangohil.urlshortener.support.TestFixtures;
import com.darshangohil.urlshortener.web.security.RegistrationLimiter;
import com.darshangohil.urlshortener.web.security.RegistrationSignIn;
import com.darshangohil.urlshortener.web.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@EnableConfigurationProperties(RateLimitProperties.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, SecurityUtils.class, LoginThrottle.class, PasswordPolicy.class,
        RegistrationLimiter.class})
class AuthControllerWebTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    // signing in for real needs the session registry and a database: RegistrationIntegrationTest
    @MockitoBean RegistrationSignIn registrationSignIn;

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Sign In · URL Shortener</title>")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void loginPageShowsAnErrorBanner() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid email or password")));
    }

    @Test
    void registerPageIsPublic() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Create Account · URL Shortener</title>")))
                .andExpect(content().string(containsString("confirmPassword")));
    }

    @Test
    void validRegistrationSignsTheUserInAndOpensMyUrls() throws Exception {
        given(userService.registerUser(any())).willReturn(new UserDto(5L, "New User"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "New User")
                        .param("email", "new.user@example.com")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-urls"))
                .andExpect(flash().attribute("successMessage", "Welcome, New User! Your account is ready."));

        var captor = ArgumentCaptor.forClass(CreateUserCmd.class);
        verify(userService).registerUser(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("new.user@example.com");
        verify(registrationSignIn).signIn(eq("new.user@example.com"), any(), any());
    }

    @Test
    void aSignedInUserIsSentToMyUrlsInsteadOfTheRegisterPage() throws Exception {
        var member = user(TestFixtures.principal(3L, "Signed In", Role.ROLE_USER));

        mockMvc.perform(get("/register").with(member))
                .andExpect(redirectedUrl("/my-urls"));
        mockMvc.perform(post("/register").with(csrf()).with(member)
                        .param("name", "Second Account")
                        .param("email", "second@example.com")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(redirectedUrl("/my-urls"));
        verify(userService, never()).registerUser(any());
    }

    @Test
    void anEmailTooLongForTheDatabaseIsAFormError() throws Exception {
        // a well-formed address of 308 characters; the column holds 255, so this used to be a 500
        String email = "a".repeat(60) + "@" + ("b".repeat(60) + ".").repeat(4) + "com";

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "New User")
                        .param("email", email)
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email must be at most 255 characters")));
        verify(userService, never()).registerUser(any());
    }

    @Test
    void tooManySignUpsFromOneAddressAreRefused() throws Exception {
        given(userService.registerUser(any())).willReturn(new UserDto(5L, "New User"));
        // an address of its own, so other tests' sign-ups don't count towards this limit
        RequestPostProcessor fromOneAddress = request -> {
            request.setRemoteAddr("203.0.113.7");
            return request;
        };
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/register").with(csrf()).with(fromOneAddress)
                            .param("name", "New User")
                            .param("email", "user" + i + "@example.com")
                            .param("password", "s3cretpassword")
                            .param("confirmPassword", "s3cretpassword"))
                    .andExpect(redirectedUrl("/my-urls"));
        }

        mockMvc.perform(post("/register").with(csrf()).with(fromOneAddress)
                        .param("name", "New User")
                        .param("email", "user5@example.com")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("Too many sign-ups from your network")));
        verify(userService, times(5)).registerUser(any());
    }

    @Test
    void invalidAttemptsDoNotCountTowardsTheSignUpLimit() throws Exception {
        RequestPostProcessor fromOneAddress = request -> {
            request.setRemoteAddr("203.0.113.8");
            return request;
        };
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/register").with(csrf()).with(fromOneAddress)
                            .param("name", "New User")
                            .param("email", "new.user@example.com")
                            .param("password", "s3cretpassword")
                            .param("confirmPassword", "typo"))
                    .andExpect(status().isOk());
        }
        given(userService.registerUser(any())).willReturn(new UserDto(5L, "New User"));

        mockMvc.perform(post("/register").with(csrf()).with(fromOneAddress)
                        .param("name", "New User")
                        .param("email", "new.user@example.com")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(redirectedUrl("/my-urls"));
    }

    @Test
    void mismatchedPasswordsAreReportedOnTheConfirmField() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "New User")
                        .param("email", "new.user@example.com")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "somethingelse"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("Passwords do not match")));
        verify(userService, never()).registerUser(any());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "New User")
                        .param("email", "new.user@example.com")
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("between 8 and 72 characters")));
        verify(userService, never()).registerUser(any());
    }

    @Test
    void malformedEmailIsRejected() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "New User")
                        .param("email", "not-an-email")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a valid email address")));
        verify(userService, never()).registerUser(any());
    }

    @Test
    void duplicateEmailIsReportedOnTheEmailField() throws Exception {
        given(userService.registerUser(any()))
                .willThrow(new EmailAlreadyExistsException("Email already registered"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "John Doe")
                        .param("email", "john.doe@example.com")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("An account with that email already exists")));
    }

    @Test
    void registrationWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .param("name", "New User")
                        .param("email", "new.user@example.com")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(status().isForbidden());
        verify(userService, never()).registerUser(any());
    }

    @Test
    void aPasswordOver72BytesIsAFormErrorNotACrash() throws Exception {
        // 60 Java characters, so it passes the length check, but 120 bytes: BCrypt
        // would throw, and the page used to be a 500
        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "New User")
                        .param("email", "new.user@example.com")
                        .param("password", "😀".repeat(30))
                        .param("confirmPassword", "😀".repeat(30)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("under 72 bytes")));
        verify(userService, never()).registerUser(any());
    }

    @Test
    void aCommonPasswordIsRefused() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "New User")
                        .param("email", "new.user@example.com")
                        .param("password", "password123")
                        .param("confirmPassword", "password123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That password is too common.")));
        verify(userService, never()).registerUser(any());
    }

    @Test
    void aTamperedLockedParameterShowsTheGenericMessage() throws Exception {
        mockMvc.perform(get("/login").param("locked", "never. Call 555-0100 to unlock"))
                .andExpect(content().string(containsString("Try again later.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("555-0100"))));
    }

    @Test
    void theLockedOutMessageShowsOnTheLoginPage() throws Exception {
        mockMvc.perform(get("/login").param("locked", "15"))
                .andExpect(content().string(containsString("Too many failed sign-in attempts. Try again in 15 minutes.")));
    }
}
