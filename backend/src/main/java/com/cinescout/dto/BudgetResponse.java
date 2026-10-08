package com.cinescout.dto;

import com.cinescout.domain.BudgetCategory;
import com.cinescout.domain.BudgetItem;
import com.cinescout.domain.BudgetStatus;
import com.cinescout.domain.Location;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A production's budget: its total and currency, every line, the sums (all planned lines, those committed or paid, those
 * paid) with what is left of the total, the sum per category, and the confirmed venues no line is for yet, to add.
 */
public record BudgetResponse(
        String currency,
        BigDecimal total,
        Totals totals,
        List<CategoryTotal> byCategory,
        List<Item> items,
        List<UnbudgetedVenue> unbudgetedVenues
) {

    /** {@code remaining} is the total less everything planned; null while there is no total. */
    public record Totals(BigDecimal planned, BigDecimal committed, BigDecimal paid, BigDecimal remaining) {
    }

    public record CategoryTotal(BudgetCategory category, BigDecimal amount) {
    }

    public record Item(UUID id, BudgetCategory category, String label, BigDecimal amount, BudgetStatus status, String note,
                       UUID locationId, String venueName, UUID sceneId, String addedByName, Instant createdAt, Instant updatedAt) {

        public static Item from(BudgetItem item) {
            Location venue = item.getLocation();
            return new Item(item.getId(), item.getCategory(), item.getLabel(), item.getAmount(), item.getStatus(), item.getNote(),
                    venue == null ? null : venue.getId(), venue == null ? null : venue.getName(),
                    venue == null ? null : venue.getScene().getId(),
                    item.getAddedBy() == null ? null : item.getAddedBy().getDisplayName(), item.getCreatedAt(), item.getUpdatedAt());
        }
    }

    /** A confirmed venue with no budget line yet: its quote as written ("£800 a day") and how many days its scene shoots. */
    public record UnbudgetedVenue(UUID locationId, String name, UUID sceneId, String sceneTitle, String quote, Integer shootDays) {
    }
}
