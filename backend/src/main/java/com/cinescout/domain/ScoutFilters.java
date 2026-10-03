package com.cinescout.domain;

import com.cinescout.logistics.GeoPoint;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What a scouting run keeps to, beyond the project's area: a base point (an address, or a spot picked on the map)
 * with a radius, a maximum day rate, venue types to leave out, and whether private property is wanted. Every field
 * is optional; stored on the project as its defaults and sent with a run to override them.
 *
 * @param baseAddress    where the radius is measured from, as an address; geocoded when no coordinates are given
 * @param baseLatitude   with {@code baseLongitude}, a spot picked on the map; wins over the address
 * @param radiusKm       how far from the base point a venue may be; needs a base point
 * @param maxBudget      the most a venue may ask for a shooting day, in the local currency; a venue whose page gives
 *                       no price is kept
 * @param excludedTypes  kinds of place to leave out, as free tags ("church", "nightclub")
 * @param includePrivate false leaves out private property (homes, privately owned places); null means true
 */
public record ScoutFilters(
        @Size(max = 300) String baseAddress,
        @DecimalMin("-90") @DecimalMax("90") Double baseLatitude,
        @DecimalMin("-180") @DecimalMax("180") Double baseLongitude,
        @DecimalMin("0.2") @DecimalMax("100") Double radiusKm,
        @PositiveOrZero @Max(10_000_000) Integer maxBudget,
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> excludedTypes,
        Boolean includePrivate
) {

    public static final ScoutFilters NONE = new ScoutFilters(null, null, null, null, null, List.of(), null);

    public ScoutFilters {
        baseAddress = baseAddress == null || baseAddress.isBlank() ? null : baseAddress.strip();
        excludedTypes = excludedTypes == null ? List.of() : excludedTypes.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    @JsonIgnore
    @AssertTrue(message = "baseLatitude and baseLongitude must be given together")
    public boolean isCoordinatePairComplete() {
        return (baseLatitude == null) == (baseLongitude == null);
    }

    @JsonIgnore
    @AssertTrue(message = "a radius needs a base point: an address or a spot on the map")
    public boolean isRadiusAnchored() {
        return radiusKm == null || hasBase();
    }

    @JsonIgnore
    public boolean hasBase() {
        return baseLatitude != null || baseAddress != null;
    }

    /** The spot picked on the map, or null when the base is an address (or there is none). */
    @JsonIgnore
    public GeoPoint pickedPoint() {
        return baseLatitude == null || baseLongitude == null ? null : new GeoPoint(baseLatitude, baseLongitude);
    }

    @JsonIgnore
    public boolean privateAllowed() {
        return !Boolean.FALSE.equals(includePrivate);
    }

    /** The excluded types in lower case, for matching. */
    @JsonIgnore
    public List<String> excludedLowerCase() {
        return excludedTypes.stream().map(tag -> tag.toLowerCase(Locale.ROOT)).toList();
    }
}
