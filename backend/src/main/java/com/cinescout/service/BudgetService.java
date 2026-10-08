package com.cinescout.service;

import com.cinescout.domain.BudgetCategory;
import com.cinescout.domain.BudgetItem;
import com.cinescout.domain.BudgetStatus;
import com.cinescout.domain.Location;
import com.cinescout.domain.LocationStatus;
import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.Scene;
import com.cinescout.dto.BudgetItemRequest;
import com.cinescout.dto.BudgetResponse;
import com.cinescout.dto.BudgetResponse.CategoryTotal;
import com.cinescout.dto.BudgetResponse.Item;
import com.cinescout.dto.BudgetResponse.Totals;
import com.cinescout.dto.BudgetResponse.UnbudgetedVenue;
import com.cinescout.dto.BudgetSettingsRequest;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.BudgetItemRepository;
import com.cinescout.repository.LocationRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A production's budget: viewers read it, editors change it. */
@Service
public class BudgetService {

    /** How many lines a production may have. */
    static final int MAX_ITEMS = 500;
    private static final int MAX_CONFIRMED = 200;

    private final BudgetItemRepository items;
    private final ProjectRepository projects;
    private final LocationRepository locations;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;

    public BudgetService(BudgetItemRepository items, ProjectRepository projects, LocationRepository locations, UserRepository users,
                         ProjectAccess access, BlockingTransactions db) {
        this.items = items;
        this.projects = projects;
        this.locations = locations;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    public Mono<BudgetResponse> get(UUID userId, UUID projectId) {
        return db.call(() -> budget(access.project(userId, projectId, ProjectRole.VIEWER)));
    }

    public Mono<BudgetResponse> settings(UUID userId, UUID projectId, BudgetSettingsRequest request) {
        return db.call(() -> {
            Project project = access.project(userId, projectId, ProjectRole.EDITOR);
            project.setBudgetTotal(request.total());
            project.setBudgetCurrency(request.currency());
            return budget(projects.saveAndFlush(project));
        });
    }

    public Mono<BudgetResponse> add(UUID userId, UUID projectId, BudgetItemRequest request) {
        return db.call(() -> {
            Project project = access.project(userId, projectId, ProjectRole.EDITOR);
            if (items.countByProject(projectId) >= MAX_ITEMS) {
                throw new ConflictException("A budget can have at most " + MAX_ITEMS + " lines; remove one first");
            }
            BudgetItem item = new BudgetItem(project, users.getReferenceById(userId));
            apply(item, request, projectId);
            items.saveAndFlush(item);
            return budget(project);
        });
    }

    public Mono<BudgetResponse> update(UUID userId, UUID itemId, BudgetItemRequest request) {
        return db.call(() -> {
            BudgetItem item = find(userId, itemId);
            apply(item, request, item.getProject().getId());
            items.saveAndFlush(item);
            return budget(item.getProject());
        });
    }

    public Mono<BudgetResponse> remove(UUID userId, UUID itemId) {
        return db.call(() -> {
            BudgetItem item = find(userId, itemId);
            Project project = item.getProject();
            items.delete(item);
            items.flush();
            return budget(project);
        });
    }

    private void apply(BudgetItem item, BudgetItemRequest request, UUID projectId) {
        item.setCategory(request.category());
        item.setLabel(request.label().strip());
        item.setAmount(request.amount());
        item.setStatus(request.status() == null ? BudgetStatus.ESTIMATE : request.status());
        item.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        item.setLocation(request.locationId() == null ? null : locations.findById(request.locationId())
                .filter(venue -> venue.getScene().getProject().getId().equals(projectId))
                .orElseThrow(() -> new InvalidRequestException("locationId", "Pick one of this production's venues")));
    }

    /** A line the user may change: 404 when it does not exist or the user is not on its project, 403 for a viewer. */
    private BudgetItem find(UUID userId, UUID itemId) {
        BudgetItem item = items.findWithProject(itemId).orElseThrow(() -> new NotFoundException("Budget line", itemId));
        try {
            access.project(userId, item.getProject().getId(), ProjectRole.EDITOR);
        } catch (NotFoundException e) {
            throw new NotFoundException("Budget line", itemId);
        }
        return item;
    }

    private BudgetResponse budget(Project project) {
        List<BudgetItem> lines = items.findByProject(project.getId());
        BigDecimal planned = BigDecimal.ZERO;
        BigDecimal committed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        Map<BudgetCategory, BigDecimal> byCategory = new EnumMap<>(BudgetCategory.class);
        Set<UUID> budgetedVenues = new HashSet<>();
        for (BudgetItem line : lines) {
            planned = planned.add(line.getAmount());
            if (line.getStatus() != BudgetStatus.ESTIMATE) {
                committed = committed.add(line.getAmount());
            }
            if (line.getStatus() == BudgetStatus.PAID) {
                paid = paid.add(line.getAmount());
            }
            byCategory.merge(line.getCategory(), line.getAmount(), BigDecimal::add);
            if (line.getLocation() != null) {
                budgetedVenues.add(line.getLocation().getId());
            }
        }
        BigDecimal remaining = project.getBudgetTotal() == null ? null : project.getBudgetTotal().subtract(planned);
        List<UnbudgetedVenue> unbudgeted = locations.findProjectShortlist(project.getId(), List.of(LocationStatus.CONFIRMED),
                        PageRequest.of(0, MAX_CONFIRMED)).stream()
                .filter(venue -> !budgetedVenues.contains(venue.getId()))
                .map(BudgetService::unbudgeted)
                .toList();
        return new BudgetResponse(project.getBudgetCurrency(), project.getBudgetTotal(), new Totals(planned, committed, paid, remaining),
                byCategory.entrySet().stream().map(entry -> new CategoryTotal(entry.getKey(), entry.getValue())).toList(),
                lines.stream().map(Item::from).toList(), unbudgeted);
    }

    private static UnbudgetedVenue unbudgeted(Location venue) {
        Scene scene = venue.getScene();
        Integer days = scene.getShootDateStart() == null ? null
                : (int) ChronoUnit.DAYS.between(scene.getShootDateStart(),
                        scene.getShootDateEnd() == null ? scene.getShootDateStart() : scene.getShootDateEnd()) + 1;
        return new UnbudgetedVenue(venue.getId(), venue.getName(), scene.getId(), scene.getTitle(), venue.getQuote(), days);
    }
}
