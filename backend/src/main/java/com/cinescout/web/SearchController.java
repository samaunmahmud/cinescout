package com.cinescout.web;

import com.cinescout.dto.SearchResponse;
import com.cinescout.security.AuthenticatedUser;
import com.cinescout.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Validated
@Tag(name = "Search", description = "Quick search across the projects you are on, for the command palette.")
class SearchController {

    private final SearchService search;

    SearchController(SearchService search) {
        this.search = search;
    }

    @Operation(summary = "Find projects, scenes and venues by name",
            description = "Up to 6 of each, names starting with the words first. Fewer than 2 characters finds nothing.")
    @GetMapping("/api/search")
    Mono<SearchResponse> search(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return search.search(user.id(), q);
    }
}
