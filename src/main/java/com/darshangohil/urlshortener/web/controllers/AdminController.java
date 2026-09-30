package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.exception.SelfModificationException;
import com.darshangohil.urlshortener.domain.models.OwnerFilter;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.models.Role;
import com.darshangohil.urlshortener.domain.models.UserDto;
import com.darshangohil.urlshortener.domain.models.UserSummary;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
import com.darshangohil.urlshortener.domain.services.UserService;
import com.darshangohil.urlshortener.web.utils.FilterLinks;
import com.darshangohil.urlshortener.web.utils.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything here is behind {@code hasRole("ADMIN")} in SecurityConfig, and the service
 * methods it calls re-check that with {@code @PreAuthorize}.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ShortUrlService shortUrlService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final String baseUrl;

    public AdminController(ShortUrlService shortUrlService,
                           UserService userService,
                           SecurityUtils securityUtils,
                           ApplicationProperties properties) {
        this.shortUrlService = shortUrlService;
        this.userService = userService;
        this.securityUtils = securityUtils;
        this.baseUrl = properties.baseUrl();
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/admin/links";
    }

    @GetMapping("/links")
    public String links(@RequestParam(defaultValue = "1") int page,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) String visibility,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String sort,
                        @RequestParam(required = false) String owner,
                        Model model) {
        ShortUrlFilter filter = ShortUrlFilter.parse(q, visibility, status, sort);
        OwnerFilter ownerFilter = OwnerFilter.parse(owner);
        if (page < 1) {
            return "redirect:" + linksLink(filter, ownerFilter, null);
        }
        PagedResult<ShortUrlDto> shortUrls = shortUrlService.findAllShortUrls(filter, ownerFilter, page);
        if (shortUrls.isBeyondLastPage()) {
            return "redirect:" + linksLink(filter, ownerFilter, shortUrls.totalPages());
        }

        model.addAttribute("shortUrls", shortUrls);
        model.addAttribute("filter", filter);
        model.addAttribute("ownerLabel", ownerLabel(ownerFilter));
        model.addAttribute("hiddenFilterParams", ownerParams(ownerFilter));
        if (filter.isFiltering() || ownerFilter.kind() != OwnerFilter.Kind.ANYONE) {
            model.addAttribute("emptyMessage", "No links match these filters.");
        }
        // the edit page sends an admin back here rather than to their own My URLs
        model.addAttribute("editFrom", "admin");
        model.addAttribute("adminActions", true);
        model.addAttribute("returnTo", linksLink(filter, ownerFilter, page));
        model.addAttribute("activeNav", "admin");
        model.addAttribute("adminTab", "links");
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("paginationUrl", linksLink(filter, ownerFilter, null));
        return "admin/links";
    }

    @PostMapping("/links/{id}/disable")
    public String disable(@PathVariable Long id,
                          @RequestParam(required = false) String returnTo,
                          RedirectAttributes redirectAttributes) {
        ShortUrlDto link = shortUrlService.setDisabled(id, true);
        redirectAttributes.addFlashAttribute("successMessage",
                "Disabled " + link.shortKey() + ": it no longer redirects");
        return "redirect:" + safeReturn(returnTo, "/admin/links");
    }

    @PostMapping("/links/{id}/enable")
    public String enable(@PathVariable Long id,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes redirectAttributes) {
        ShortUrlDto link = shortUrlService.setDisabled(id, false);
        redirectAttributes.addFlashAttribute("successMessage", "Re-enabled " + link.shortKey());
        return "redirect:" + safeReturn(returnTo, "/admin/links");
    }

    /**
     * Back to the listing the admin was on, filters and page included. Only
     * {@code listing} itself, optionally with a query string, is accepted, so the
     * parameter cannot redirect anywhere else.
     */
    private static String safeReturn(String returnTo, String listing) {
        if (returnTo != null && returnTo.startsWith(listing)
                && (returnTo.length() == listing.length() || returnTo.charAt(listing.length()) == '?')) {
            return returnTo;
        }
        return listing;
    }

    // --- users -----------------------------------------------------------------------

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "1") int page,
                        @RequestParam(required = false) String q,
                        Model model) {
        String query = q == null || q.isBlank() ? null : q.strip();
        if (page < 1) {
            return "redirect:" + usersLink(query, null);
        }
        PagedResult<UserSummary> users = userService.findUsers(query, page);
        if (users.isBeyondLastPage()) {
            return "redirect:" + usersLink(query, users.totalPages());
        }
        model.addAttribute("users", users);
        model.addAttribute("query", query);
        model.addAttribute("currentUserId", securityUtils.getCurrentUserId());
        model.addAttribute("returnTo", usersLink(query, page));
        model.addAttribute("activeNav", "admin");
        model.addAttribute("adminTab", "users");
        model.addAttribute("paginationUrl", usersLink(query, null));
        return "admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam Role role,
                             @RequestParam(required = false) String returnTo,
                             RedirectAttributes redirectAttributes) {
        try {
            UserDto user = userService.changeRole(id, role, securityUtils.getCurrentUserId());
            redirectAttributes.addFlashAttribute("successMessage", user.name()
                    + (role == Role.ROLE_ADMIN ? " is now an admin" : " is no longer an admin"));
        } catch (SelfModificationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + safeReturn(returnTo, "/admin/users");
    }

    @PostMapping("/users/{id}/disable")
    public String disableUser(@PathVariable Long id,
                              @RequestParam(required = false) String returnTo,
                              RedirectAttributes redirectAttributes) {
        return setUserEnabled(id, false, returnTo, redirectAttributes);
    }

    @PostMapping("/users/{id}/enable")
    public String enableUser(@PathVariable Long id,
                             @RequestParam(required = false) String returnTo,
                             RedirectAttributes redirectAttributes) {
        return setUserEnabled(id, true, returnTo, redirectAttributes);
    }

    private String setUserEnabled(Long id, boolean enabled, String returnTo,
                                  RedirectAttributes redirectAttributes) {
        try {
            UserDto user = userService.setEnabled(id, enabled, securityUtils.getCurrentUserId());
            redirectAttributes.addFlashAttribute("successMessage", enabled
                    ? "Re-enabled " + user.name()
                    : "Disabled " + user.name() + ": they are signed out and can't sign in");
        } catch (SelfModificationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + safeReturn(returnTo, "/admin/users");
    }

    private static String usersLink(String query, Integer page) {
        UriComponentsBuilder link = UriComponentsBuilder.fromPath("/admin/users");
        if (query != null) {
            link.queryParam("q", query);
        }
        if (page != null) {
            link.queryParam("page", page);
        }
        return link.encode().toUriString();
    }

    @PostMapping("/delete-urls")
    public String deleteShortUrls(@RequestParam(name = "ids", required = false) List<Long> ids,
                                  RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No URLs selected for deletion");
            return "redirect:/admin/links";
        }
        shortUrlService.deleteShortUrls(ids);
        redirectAttributes.addFlashAttribute("successMessage",
                ids.size() == 1 ? "Short URL deleted" : ids.size() + " short URLs deleted");
        return "redirect:/admin/links";
    }

    /** "Links by …" for the banner above a single-owner listing; null when showing everyone. */
    private String ownerLabel(OwnerFilter owner) {
        return switch (owner.kind()) {
            case ANYONE -> null;
            case GUESTS -> "guests";
            case USER -> userService.findUser(owner.userId())
                    .map(user -> user.name())
                    .orElse("user #" + owner.userId());
        };
    }

    private static Map<String, String> ownerParams(OwnerFilter owner) {
        Map<String, String> params = new LinkedHashMap<>();
        if (owner.toParam() != null) {
            params.put("owner", owner.toParam());
        }
        return params;
    }

    private static String linksLink(ShortUrlFilter filter, OwnerFilter owner, Integer page) {
        return FilterLinks.build("/admin/links", filter, ownerParams(owner), page);
    }
}
