package com.cinescout.logistics.weather;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Which weather each shoot date can honestly get. A forecast only reaches about two weeks ahead, so a
 * shoot planned months out is shown the weather recorded on the same date in the most recent past year,
 * labelled as such rather than passed off as a forecast. Past dates get what was recorded.
 *
 * @param entries one per shoot date, in the order given
 */
public record WeatherPlan(List<Entry> entries) {

    /** Reanalysis archives run about five days behind; a week keeps clear of the gap. */
    static final int HISTORY_LAG_DAYS = 7;

    public enum Basis {
        /** A forecast for an upcoming day. */
        FORECAST,
        /** What the weather was on a day that has passed. */
        RECORDED,
        /** Too far ahead to forecast: the weather recorded on the same date in an earlier year. */
        PAST_YEAR
    }

    /**
     * @param referenceDate the date whose weather is looked up: the shoot date itself, or the same date in
     *                      an earlier year for {@link Basis#PAST_YEAR}
     * @param fromForecast  whether the value comes from the forecast model (upcoming and recent days)
     *                      rather than the history archive
     */
    public record Entry(LocalDate date, Basis basis, LocalDate referenceDate, boolean fromForecast) {
    }

    /**
     * @param forecastDaysAhead how many days after today the provider can forecast
     * @param forecastDaysBack  how many days before today the forecast model still serves
     */
    public static WeatherPlan of(List<LocalDate> dates, LocalDate today, int forecastDaysAhead, int forecastDaysBack) {
        LocalDate lastForecastDay = today.plusDays(forecastDaysAhead);
        LocalDate firstForecastModelDay = today.minusDays(forecastDaysBack);
        LocalDate lastHistoryDay = today.minusDays(HISTORY_LAG_DAYS);
        return new WeatherPlan(dates.stream().map(date -> {
            if (date.isAfter(lastForecastDay)) {
                LocalDate reference = date;
                while (reference.isAfter(lastHistoryDay)) {
                    reference = reference.minusYears(1);
                }
                return new Entry(date, Basis.PAST_YEAR, reference, false);
            }
            if (!date.isBefore(today)) {
                return new Entry(date, Basis.FORECAST, date, true);
            }
            return new Entry(date, Basis.RECORDED, date, !date.isBefore(firstForecastModelDay));
        }).toList());
    }

    /** The span of reference dates to ask the forecast model for, if any. */
    public Optional<Range> forecastRange() {
        return range(Entry::fromForecast);
    }

    /** The span of reference dates to ask the history archive for, if any. */
    public Optional<Range> historyRange() {
        return range(entry -> !entry.fromForecast());
    }

    private Optional<Range> range(Predicate<Entry> which) {
        List<LocalDate> references = entries.stream().filter(which).map(Entry::referenceDate).sorted().toList();
        return references.isEmpty() ? Optional.empty() : Optional.of(new Range(references.getFirst(), references.getLast()));
    }

    /** Inclusive. */
    public record Range(LocalDate from, LocalDate to) {
    }
}
