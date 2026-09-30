package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.exception.InvalidUrlException;
import com.darshangohil.urlshortener.domain.exception.ShortUrlNotFoundException;
import com.darshangohil.urlshortener.domain.models.CreateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
import com.darshangohil.urlshortener.web.dtos.CreateShortUrlForm;
import com.darshangohil.urlshortener.web.utils.SecurityUtils;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ShortUrlService shortUrlService;
    private final String baseUrl;
    private final SecurityUtils securityUtils;

    public HomeController(ShortUrlService shortUrlService,
                          ApplicationProperties properties, SecurityUtils securityUtils) {
        this.shortUrlService = shortUrlService;
        this.baseUrl = properties.baseUrl();
        this.securityUtils = securityUtils;
    }

    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "1") int page, Model model) {
        if (page < 1) {
            return "redirect:/";
        }
        PagedResult<ShortUrlDto> shortUrls = addHomeAttributes(model, page);
        if (shortUrls.isBeyondLastPage()) {
            return "redirect:/?page=" + shortUrls.totalPages();
        }
        model.addAttribute("createShortUrlForm", new CreateShortUrlForm());
        return "index";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("activeNav", "about");
        return "about";
    }

    @PostMapping("/short-urls")
    public String createShortUrl(
            @ModelAttribute("createShortUrlForm") @Valid CreateShortUrlForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Model model){
        if(bindingResult.hasErrors()){
            addHomeAttributes(model, 1);
            return "index";
        }

        try{
            Long userId = securityUtils.getCurrentUserId();
            CreateShortUrlCmd cmd = new CreateShortUrlCmd(
                    form.originalUrl(),
                    form.isPrivate(),
                    form.expirationInDays(),
                    userId
            );
            var shortUrlDto = shortUrlService.createShortUrl(cmd);
            redirectAttributes.addFlashAttribute("successMessage", "URL shortened created successfully! "
            + baseUrl + "/s/" + shortUrlDto.shortKey());
        }catch (InvalidUrlException e){
            // an unreachable URL is bad user input, not a server failure -- report it on the field
            log.info("Rejected unreachable URL {}", form.originalUrl());
            bindingResult.rejectValue("originalUrl", "url.unreachable",
                    "We couldn't reach that URL. Check it and try again.");
            addHomeAttributes(model, 1);
            return "index";
        }catch (DataAccessException e){
            log.error("Failed to create short URL for {}", form.originalUrl(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to create Short URL");
        }
        return "redirect:/";
    }

    @GetMapping("/s/{shortKey}")
    public String redirectToOriginalUrl(@PathVariable String shortKey) {
        // a private link resolves only for its owner; everyone else gets the same
        // 404 an unknown key would produce
        Long userId = securityUtils.getCurrentUserId();
        return shortUrlService.accessOriginalUrl(shortKey, userId)
                .map(originalUrl -> "redirect:" + originalUrl)
                .orElseThrow(() -> new ShortUrlNotFoundException(
                        "No accessible short URL for key: " + shortKey));
    }

    @GetMapping("/my-urls")
    public String myUrls(@RequestParam(defaultValue = "1") int page, Model model) {
        if (page < 1) {
            return "redirect:/my-urls";
        }
        SecurityUser currentUser = securityUtils.getCurrentUser().orElseThrow();
        PagedResult<ShortUrlDto> shortUrls =
                shortUrlService.findUrlsByUser(currentUser.getId(), page);
        if (shortUrls.isBeyondLastPage()) {
            return "redirect:/my-urls?page=" + shortUrls.totalPages();
        }

        model.addAttribute("shortUrls", shortUrls);
        model.addAttribute("stats", shortUrlService.getUserStats(currentUser.getId()));
        model.addAttribute("activeNav", "my-urls");
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("paginationUrl", "/my-urls");
        return "my-urls";
    }

    @PostMapping("/delete-urls")
    public String deleteShortUrls(@RequestParam(name = "ids", required = false) List<Long> ids,
                                  RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No URLs selected for deletion");
            return "redirect:/my-urls";
        }
        shortUrlService.deleteShortUrls(ids);
        redirectAttributes.addFlashAttribute("successMessage",
                ids.size() == 1 ? "Short URL deleted" : ids.size() + " short URLs deleted");
        return "redirect:/my-urls";
    }

    private PagedResult<ShortUrlDto> addHomeAttributes(Model model, int page) {
        PagedResult<ShortUrlDto> shortUrls = shortUrlService.findAllPublicShortUrls(page);

        model.addAttribute("shortUrls", shortUrls);
        model.addAttribute("activeNav", "home");
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("paginationUrl", "/");
        return shortUrls;
    }
}
