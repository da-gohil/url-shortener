package com.darshangohil.urlshortener.domain.services;

import com.darshangohil.urlshortener.domain.models.AdminOverview;
import com.darshangohil.urlshortener.domain.models.AdminOverview.DailyCount;
import com.darshangohil.urlshortener.domain.models.UserUrlStats;
import com.darshangohil.urlshortener.domain.repository.ShortUrlRepository;
import com.darshangohil.urlshortener.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AdminOverviewServiceTest {

    // 2026-09-30 10:00 in New York; the server's "today" is the 30th
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T14:00:00Z"), NEW_YORK);

    private ShortUrlRepository shortUrlRepository;
    private AdminOverviewService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        shortUrlRepository = mock(ShortUrlRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        given(userRepository.count()).willReturn(4L);
        given(shortUrlRepository.getSiteStats(any())).willReturn(new UserUrlStats(10L, 99L, 7L, 1L));
        given(shortUrlRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(Page.empty());
        service = new AdminOverviewService(shortUrlRepository, userRepository, new EntityMapper(), CLOCK);
    }

    @Test
    void theChartCoversFourteenDaysEndingTodayWithEmptyDaysFilledIn() {
        given(shortUrlRepository.findCreatedAtSince(any())).willReturn(List.of());

        List<DailyCount> days = service.getOverview().linksPerDay();

        assertThat(days).hasSize(AdminOverviewService.CHART_DAYS);
        assertThat(days.getFirst().day()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(days.getLast().day()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(days).allMatch(day -> day.count() == 0);
    }

    @Test
    void linksAreCountedOnTheServersLocalDay() {
        given(shortUrlRepository.findCreatedAtSince(any())).willReturn(List.of(
                Instant.parse("2026-09-30T13:00:00Z"),   // 09:00 on the 30th in New York
                Instant.parse("2026-09-30T02:00:00Z"),   // 22:00 on the 29th in New York
                Instant.parse("2026-09-29T15:00:00Z"))); // 11:00 on the 29th

        AdminOverview overview = service.getOverview();

        assertThat(overview.linksPerDay().getLast()).isEqualTo(new DailyCount(LocalDate.of(2026, 9, 30), 1));
        assertThat(overview.linksPerDay().get(12)).isEqualTo(new DailyCount(LocalDate.of(2026, 9, 29), 2));
        assertThat(overview.linksInWindow()).isEqualTo(3);
        assertThat(overview.busiestDayCount()).isEqualTo(2);
        assertThat(overview.peakDay()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void siteTotalsComeStraightFromTheRepositories() {
        given(shortUrlRepository.findCreatedAtSince(any())).willReturn(List.of());

        AdminOverview overview = service.getOverview();

        assertThat(overview.totalUsers()).isEqualTo(4);
        assertThat(overview.links()).isEqualTo(new UserUrlStats(10L, 99L, 7L, 1L));
    }

    @Test
    void anEmptyChartStillHasANonZeroScale() {
        var empty = new AdminOverview(0, new UserUrlStats(0L, 0L, 0L, 0L),
                List.of(new DailyCount(LocalDate.of(2026, 9, 30), 0)), List.of());

        // bar heights divide by this, so it must never be 0
        assertThat(empty.busiestDayCount()).isEqualTo(1);
    }
}
