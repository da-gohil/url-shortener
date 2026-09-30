package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Everything here is behind {@code hasRole("ADMIN")} in SecurityConfig, and the service
 * methods it calls re-check that with {@code @PreAuthorize}.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ShortUrlService shortUrlService;
    private final String baseUrl;

    public AdminController(ShortUrlService shortUrlService,
                           ApplicationProperties properties) {
        this.shortUrlService = shortUrlService;
        this.baseUrl = properties.baseUrl();
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "1") int page, Model model) {
        if (page < 1) {
            return "redirect:/admin/dashboard";
        }
        PagedResult<ShortUrlDto> shortUrls = shortUrlService.findAllShortUrls(page);
        if (shortUrls.isBeyondLastPage()) {
            return "redirect:/admin/dashboard?page=" + shortUrls.totalPages();
        }

        model.addAttribute("shortUrls", shortUrls);
        model.addAttribute("activeNav", "admin");
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("paginationUrl", "/admin/dashboard");
        return "admin/dashboard";
    }

    @PostMapping("/delete-urls")
    public String deleteShortUrls(@RequestParam(name = "ids", required = false) List<Long> ids,
                                  RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No URLs selected for deletion");
            return "redirect:/admin/dashboard";
        }
        shortUrlService.deleteShortUrls(ids);
        redirectAttributes.addFlashAttribute("successMessage",
                ids.size() == 1 ? "Short URL deleted" : ids.size() + " short URLs deleted");
        return "redirect:/admin/dashboard";
    }
}
