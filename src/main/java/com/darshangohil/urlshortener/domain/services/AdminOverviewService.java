package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.models.AdminOverview;
import com.darshangohil.urlshortener.domain.models.AdminOverview.DailyCount;
import com.darshangohil.urlshortener.domain.models.ShortUrlFilter;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Site-wide numbers for the admin overview. */
@Service
@Transactional(readOnly = true)
public class AdminOverviewService {

    /** How many days the "links created" chart covers, today included. */
    static final int CHART_DAYS = 14;
    private static final int TOP_LINKS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    private final Clock clock;

    // two constructors, so Spring needs telling which one; the other takes a Clock for tests
    @Autowired
    public AdminOverviewService(ShortUrlRepository shortUrlRepository,
                                UserRepository userRepository,
                                EntityMapper entityMapper) {
        this(shortUrlRepository, userRepository, entityMapper, Clock.systemDefaultZone());
    }

    AdminOverviewService(ShortUrlRepository shortUrlRepository, UserRepository userRepository,
                         EntityMapper entityMapper, Clock clock) {
        this.shortUrlRepository = shortUrlRepository;
        this.userRepository = userRepository;
        this.entityMapper = entityMapper;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public AdminOverview getOverview() {
        Instant now = clock.instant();
        var mostClicked = shortUrlRepository
                .findAll(Specification.unrestricted(),
                        PageRequest.of(0, TOP_LINKS, ShortUrlFilter.SortOrder.CLICKS.toSort()))
                .map(entityMapper::toShortUrlDto)
                .getContent();
        return new AdminOverview(
                userRepository.count(),
                shortUrlRepository.getSiteStats(now),
                linksPerDay(),
                mostClicked);
    }

    /**
     * Days are the server's local calendar days, the same zone the templates format
     * timestamps in. Counted here rather than with SQL date functions so the result
     * does not depend on the database session's time zone.
     */
    private List<DailyCount> linksPerDay() {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusDays(CHART_DAYS - 1);
        Instant since = firstDay.atStartOfDay(zone).toInstant();

        Map<LocalDate, Long> counts = shortUrlRepository.findCreatedAtSince(since).stream()
                .collect(Collectors.groupingBy(createdAt -> LocalDate.ofInstant(createdAt, zone),
                        Collectors.counting()));
        return firstDay.datesUntil(today.plusDays(1))
                .map(day -> new DailyCount(day, counts.getOrDefault(day, 0L)))
                .toList();
    }
}
