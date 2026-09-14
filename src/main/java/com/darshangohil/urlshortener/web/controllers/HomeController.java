package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.models.CreateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
import com.darshangohil.urlshortener.web.dtos.CreateShortUrlForm;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ShortUrlService shortUrlService;
    private final String baseUrl;

    public HomeController(ShortUrlService shortUrlService,
                          ApplicationProperties properties) {
        this.shortUrlService = shortUrlService;
        this.baseUrl = properties.baseUrl();
    }

    @GetMapping("/")
    public String home(Model model) {
        addHomeAttributes(model);
        model.addAttribute("createShortUrlForm", new CreateShortUrlForm());
        return "index";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("title", "About");
        model.addAttribute("activeNav", "about");
        return "about";
    }

    @PostMapping("/short-urls")
    public String createShortUrl(
            @Valid @ModelAttribute("createShortUrlForm") CreateShortUrlForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Model model){
        if(bindingResult.hasErrors()){
            addHomeAttributes(model);
            return "index";
        }

        try{
            CreateShortUrlCmd cmd = new CreateShortUrlCmd(form.originalUrl());
            var shortUrlDto = shortUrlService.createShortUrl(cmd);
            redirectAttributes.addFlashAttribute("successMessage", "URL shortened created successfully! "
            + baseUrl + "/s/" + shortUrlDto.shortKey());
        }catch (DataAccessException e){
            log.error("Failed to create short URL for {}", form.originalUrl(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to create Short URL");
        }
        return "redirect:/";
    }

    @GetMapping("/s/{shortKey}")
    public String redirectToOriginalUrl(@PathVariable String shortKey,
                                        RedirectAttributes redirectAttributes) {
        return shortUrlService.accessOriginalUrl(shortKey)
                .map(originalUrl -> "redirect:" + originalUrl)
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "That short link is invalid or has expired");
                    return "redirect:/";
                });
    }

    private void addHomeAttributes(Model model) {
        List<ShortUrlDto> shortUrls = shortUrlService.findAllPublicShortUrls();

        model.addAttribute("shortUrls", shortUrls);
        model.addAttribute("title", "URL Shortener Service");
        model.addAttribute("activeNav", "home");
        model.addAttribute("baseUrl", baseUrl);
    }
}
