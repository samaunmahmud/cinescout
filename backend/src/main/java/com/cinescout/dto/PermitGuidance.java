package com.cinescout.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Who to ask about filming at a venue in a public place, and how far ahead.
 *
 * @param applies      whether the venue is marked as a public space, where this matters most
 * @param areaName     the local authority area of the venue's position, as the geocoder names it; null while not known
 * @param office       the office to ask; null for NEEDS_POSITION and OUTSIDE_COVERAGE
 * @param editUrl      where to correct the guide's data; null for none
 */
public record PermitGuidance(
        Status status,
        boolean applies,
        String areaName,
        Office office,
        LocalDate lastReviewed,
        List<Source> sources,
        String editUrl
) {

    public enum Status {
        /** A listed office covers the venue's area. */
        FOUND,
        /** The venue is in the UK but in no listed area: ask the local council. */
        FALLBACK,
        /** The venue is outside the UK, which the guide does not cover. */
        OUTSIDE_COVERAGE,
        /** The venue has no position on the map yet. */
        NEEDS_POSITION,
        /** The area could not be looked up just now; the local council is the safe answer. */
        LOOKUP_FAILED
    }

    /**
     * @param leadTimeWorkingDays working days ahead to apply, for the scene's crew size; null when not known
     * @param leadTimeText        the source's own wording, crew sizes and all
     * @param listed              false for the fallback ("the local council")
     */
    public record Office(String area, String name, String contactUrl, Integer leadTimeWorkingDays, String leadTimeText, String note,
                         List<String> checklist, boolean listed) {
    }

    public record Source(String name, String url) {
    }
}
