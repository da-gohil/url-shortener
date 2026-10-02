package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.domain.exception.EmailAlreadyExistsException;
import com.darshangohil.urlshortener.domain.models.CreateUserCmd;
import com.darshangohil.urlshortener.domain.services.PasswordPolicy;
import com.darshangohil.urlshortener.domain.services.UserService;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.web.dtos.RegisterForm;
import com.darshangohil.urlshortener.web.security.RegistrationLimiter;
import com.darshangohil.urlshortener.web.security.RegistrationSignIn;
import com.darshangohil.urlshortener.web.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final PasswordPolicy passwordPolicy;
    private final RegistrationLimiter registrationLimiter;
    private final RegistrationSignIn registrationSignIn;
    private final SecurityUtils securityUtils;

    public AuthController(UserService userService,
                          PasswordPolicy passwordPolicy,
                          RegistrationLimiter registrationLimiter,
                          RegistrationSignIn registrationSignIn,
                          SecurityUtils securityUtils) {
        this.userService = userService;
        this.passwordPolicy = passwordPolicy;
        this.registrationLimiter = registrationLimiter;
        this.registrationSignIn = registrationSignIn;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("activeNav", "login");
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (isSignedIn()) {
            return "redirect:/my-urls";
        }
        model.addAttribute("activeNav", "register");
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("registerForm") @Valid RegisterForm form,
                           BindingResult bindingResult,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (isSignedIn()) {
            return "redirect:/my-urls";
        }

        // cross-field check, so it only runs once the two fields are individually valid
        if (!bindingResult.hasFieldErrors("password")
                && !bindingResult.hasFieldErrors("confirmPassword")
                && !form.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "Passwords do not match");
        }

        if (!bindingResult.hasFieldErrors("password")) {
            passwordPolicy.problems(form.password(), form.email(), form.name())
                    .forEach(problem -> bindingResult.rejectValue("password", "password.policy", problem));
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "register");
            return "register";
        }

        var limit = registrationLimiter.attempt(request.getRemoteAddr());
        if (limit.limited()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            bindingResult.reject("rate.limited",
                    "Too many sign-ups from your network. Try again in " + limit.retryAfterMinutes()
                            + (limit.retryAfterMinutes() == 1 ? " minute." : " minutes."));
            model.addAttribute("activeNav", "register");
            return "register";
        }

        UserDto user;
        try {
            user = userService.registerUser(new CreateUserCmd(form.name(), form.email(), form.password()));
        } catch (EmailAlreadyExistsException e) {
            log.info("Registration rejected, email already in use");
            bindingResult.rejectValue("email", "email.exists",
                    "An account with that email already exists");
            model.addAttribute("activeNav", "register");
            return "register";
        }

        registrationSignIn.signIn(form.email().strip(), request, response);
        redirectAttributes.addFlashAttribute("successMessage",
                "Welcome, " + user.name() + "! Your account is ready.");
        return "redirect:/my-urls";
    }

    private boolean isSignedIn() {
        return securityUtils.getCurrentUser().isPresent();
    }
}
