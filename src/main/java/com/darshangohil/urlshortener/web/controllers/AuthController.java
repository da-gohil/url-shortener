package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.domain.exception.EmailAlreadyExistsException;
import com.darshangohil.urlshortener.domain.models.CreateUserCmd;
import com.darshangohil.urlshortener.domain.services.UserService;
import com.darshangohil.urlshortener.web.dtos.RegisterForm;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("activeNav", "login");
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("activeNav", "register");
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("registerForm") @Valid RegisterForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {

        // cross-field check, so it only runs once the two fields are individually valid
        if (!bindingResult.hasFieldErrors("password")
                && !bindingResult.hasFieldErrors("confirmPassword")
                && !form.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "Passwords do not match");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "register");
            return "register";
        }

        try {
            userService.registerUser(new CreateUserCmd(form.name(), form.email(), form.password()));
        } catch (EmailAlreadyExistsException e) {
            log.info("Registration rejected, email already in use");
            bindingResult.rejectValue("email", "email.exists",
                    "An account with that email already exists");
            model.addAttribute("activeNav", "register");
            return "register";
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Account created. Please sign in.");
        return "redirect:/login";
    }
}
