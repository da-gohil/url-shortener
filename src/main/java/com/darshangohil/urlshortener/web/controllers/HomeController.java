package com.darshangohil.urlshortener.web.controllers;

import com.darshangohil.urlshortener.ApplicationProperties;
import com.darshangohil.urlshortener.domain.exception.InvalidUrlException;
import com.darshangohil.urlshortener.domain.exception.ShortUrlNotFoundException;
import com.darshangohil.urlshortener.domain.models.CreateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.PagedResult;
import com.darshangohil.urlshortener.domain.models.SecurityUser;
import com.darshangohil.urlshortener.domain.models.ShortUrlDto;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.services.ShortUrlService;
import com.darshangohil.urlshortener.web.dtos.CreateShortUrlForm;
import com.darshangohil.urlshortener.web.dtos.EditShortUrlForm;
import com.darshangohil.urlshortener.web.utils.FilterLinks;
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
import java.util.Map;

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
    public String myUrls(@RequestParam(defaultValue = "1") int page,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) String visibility,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String sort,
                         Model model) {
        ShortUrlFilter filter = ShortUrlFilter.parse(q, visibility, status, sort);
        if (page < 1) {
            return "redirect:" + myUrlsLink(filter, null);
        }
        SecurityUser currentUser = securityUtils.getCurrentUser().orElseThrow();
        PagedResult<ShortUrlDto> shortUrls =
                shortUrlService.findUrlsByUser(currentUser.getId(), filter, page);
        if (shortUrls.isBeyondLastPage()) {
            return "redirect:" + myUrlsLink(filter, shortUrls.totalPages());
        }

        model.addAttribute("shortUrls", shortUrls);
        model.addAttribute("stats", shortUrlService.getUserStats(currentUser.getId()));
        model.addAttribute("filter", filter);
        if (filter.isFiltering()) {
            model.addAttribute("emptyMessage", "No links match these filters.");
        }
        model.addAttribute("activeNav", "my-urls");
        model.addAttribute("baseUrl", baseUrl);
        // the pager appends ?page= / &page= to this, so the filters survive paging
        model.addAttribute("paginationUrl", myUrlsLink(filter, null));
        return "my-urls";
    }

    private static String myUrlsLink(ShortUrlFilter filter, Integer page) {
        return FilterLinks.build("/my-urls", filter, Map.of(), page);
    }

    @GetMapping("/my-urls/{id}/edit")
    public String editShortUrlForm(@PathVariable Long id,
                                   @RequestParam(required = false) String from,
                                   Model model) {
        ShortUrlDto shortUrl = shortUrlService.getShortUrl(id);
        model.addAttribute("editShortUrlForm",
                new EditShortUrlForm(shortUrl.isPrivate(), "keep", null));
        return showEditForm(shortUrl, isFromAdmin(from), model);
    }

    @PostMapping("/my-urls/{id}/edit")
    public String editShortUrl(@PathVariable Long id,
                               @RequestParam(required = false) String from,
                               @ModelAttribute("editShortUrlForm") @Valid EditShortUrlForm form,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        if (form.isMissingDays()) {
            bindingResult.rejectValue("expirationInDays", "expiry.days.required",
                    "Enter how many days until the link expires");
        }
        if (bindingResult.hasErrors()) {
            return showEditForm(shortUrlService.getShortUrl(id), isFromAdmin(from), model);
        }
        ShortUrlDto updated = shortUrlService.updateShortUrl(id, form.toCmd());
        redirectAttributes.addFlashAttribute("successMessage",
                "Updated " + baseUrl + "/s/" + updated.shortKey());
        return "redirect:" + (isFromAdmin(from) ? "/admin/links" : "/my-urls");
    }

    private String showEditForm(ShortUrlDto shortUrl, boolean fromAdmin, Model model) {
        model.addAttribute("shortUrl", shortUrl);
        model.addAttribute("fromAdmin", fromAdmin);
        model.addAttribute("activeNav", fromAdmin ? "admin" : "my-urls");
        model.addAttribute("baseUrl", baseUrl);
        return "edit-url";
    }

    /**
     * Where the edit page returns to. Only the one known value is honoured, so the
     * parameter can never become an open redirect.
     */
    private static boolean isFromAdmin(String from) {
        return "admin".equals(from);
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
