package com.cinescout.web;

import com.cinescout.dto.BudgetItemRequest;
import com.cinescout.dto.BudgetResponse;
import com.cinescout.dto.BudgetSettingsRequest;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@Tag(name = "Budget", description = "A production's budget: a total, lines per venue, permit, deposit and so on, and what is left.")
class BudgetController {

    private final BudgetService budget;

    BudgetController(BudgetService budget) {
        this.budget = budget;
    }

    @Operation(summary = "Get a production's budget",
            description = "The total and currency, every line in the order added, the sums (planned, committed or paid, paid, remaining) "
                    + "and per category, and the confirmed venues no line is for yet.")
    @GetMapping("/api/projects/{projectId}/budget")
    Mono<BudgetResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
        return budget.get(user.id(), projectId);
    }

    @Operation(summary = "Set a production's total budget and currency")
    @PutMapping("/api/projects/{projectId}/budget")
    Mono<BudgetResponse> settings(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                                  @Valid @RequestBody BudgetSettingsRequest request) {
        return budget.settings(user.id(), projectId, request);
    }

    @Operation(summary = "Add a budget line", description = "Answers with the whole budget. At most 500 lines a production.")
    @PostMapping("/api/projects/{projectId}/budget/items")
    @ResponseStatus(HttpStatus.CREATED)
    Mono<BudgetResponse> add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
                             @Valid @RequestBody BudgetItemRequest request) {
        return budget.add(user.id(), projectId, request);
    }

    @Operation(summary = "Change a budget line", description = "Answers with the whole budget.")
    @PutMapping("/api/budget-items/{itemId}")
    Mono<BudgetResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID itemId,
                                @Valid @RequestBody BudgetItemRequest request) {
        return budget.update(user.id(), itemId, request);
    }

    @Operation(summary = "Remove a budget line", description = "Answers with the whole budget.")
    @DeleteMapping("/api/budget-items/{itemId}")
    Mono<BudgetResponse> remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID itemId) {
        return budget.remove(user.id(), itemId);
    }
}
