package com.cinescout.ai;

import com.cinescout.ai.VenueVerdict.Evidence;
import com.cinescout.ai.VenueVerdict.SettingMatch;
import com.cinescout.domain.BookingFriction;

import java.util.List;

/** Model answers for tests that script the assessment step. */
public final class Verdicts {

    private Verdicts() {
    }

    /** One venue in the area, of the given kind, the page saying nothing about any requirement. */
    public static VenueVerdict of(SettingMatch setting) {
        return of(setting, null, null);
    }

    public static VenueVerdict of(SettingMatch setting, String venueName, String address) {
        return verdict(true, false, setting, Evidence.UNKNOWN, venueName, address, List.of());
    }

    /** The right kind of place, the page showing it meets every requirement: the best a venue can do. */
    public static VenueVerdict ideal() {
        return verdict(true, false, SettingMatch.EXACT, Evidence.MEETS, null, null, List.of());
    }

    /** The right kind of place, but the page shows it is somewhere else. */
    public static VenueVerdict elsewhere(String venueName) {
        return verdict(true, true, SettingMatch.EXACT, Evidence.UNKNOWN, venueName, null, List.of());
    }

    /** A page about several venues, naming {@code names}. */
    public static VenueVerdict directory(String... names) {
        return verdict(false, false, SettingMatch.EXACT, Evidence.UNKNOWN, null, null, List.of(names));
    }

    private static VenueVerdict verdict(boolean single, boolean elsewhere, SettingMatch setting, Evidence evidence,
                                        String venueName, String address, List<String> listed) {
        return new VenueVerdict(single, venueName, address, elsewhere, setting, evidence, evidence, evidence, evidence,
                evidence, "Reason", BookingFriction.COMMERCIAL, "Enquire via events team", List.of("Lift access only"), listed);
    }
}
