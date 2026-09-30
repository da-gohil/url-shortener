package com.darshangohil.urlshortener.domain.models;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the admin overview shows.
 *
 * @param linksPerDay one entry per day, oldest first, including days with no links
 */
public record AdminOverview(long totalUsers,
                            UserUrlStats links,
                            List<DailyCount> linksPerDay,
                            List<ShortUrlDto> mostClicked) {

    public record DailyCount(LocalDate day, long count) {
    }

    /** Tallest bar, never 0, so bar heights can be computed as a share of it. */
    public long busiestDayCount() {
        return Math.max(1, linksPerDay.stream().mapToLong(DailyCount::count).max().orElse(0));
    }

    public long linksInWindow() {
        return linksPerDay.stream().mapToLong(DailyCount::count).sum();
    }

    /** The first day with the most links, the one bar that gets a direct label. */
    public LocalDate peakDay() {
        return linksPerDay.stream()
                .filter(day -> day.count() == busiestDayCount())
                .map(DailyCount::day)
                .findFirst()
                .orElse(null);
    }
}
