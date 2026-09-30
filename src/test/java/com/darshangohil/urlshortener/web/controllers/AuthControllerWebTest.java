package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.config.SecurityConfig;
import com.darshangohil.urlshortener.domain.exception.EmailAlreadyExistsException;
import com.darshangohil.urlshortener.domain.models.CreateUserCmd;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.domain.services.UserService;
import com.darshangohil.urlshortener.web.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityUtils.class})
class AuthControllerWebTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Sign In</title>")))
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
                .andExpect(content().string(containsString("<title>Create Account</title>")))
                .andExpect(content().string(containsString("confirmPassword")));
    }

    @Test
    void validRegistrationRedirectsToLogin() throws Exception {
        given(userService.registerUser(any())).willReturn(new UserDto(5L, "New User"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "New User")
                        .param("email", "new.user@example.com")
                        .param("password", "s3cretpassword")
                        .param("confirmPassword", "s3cretpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("successMessage",
                        containsString("Account created")));

        var captor = ArgumentCaptor.forClass(CreateUserCmd.class);
        verify(userService).registerUser(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("new.user@example.com");
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
}
