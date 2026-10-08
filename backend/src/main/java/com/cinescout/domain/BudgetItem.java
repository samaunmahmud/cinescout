package com.cinescout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One line of a production's budget, in the project's currency; optionally for one of its venues. */
@Entity
@Table(name = "budget_items")
public class BudgetItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** Null when the line is not for a venue, or the venue was deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BudgetCategory category;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BudgetStatus status = BudgetStatus.ESTIMATE;

    private String note;

    /** Null once that account is gone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    protected BudgetItem() {
    }

    public BudgetItem(Project project, User addedBy) {
        this.project = project;
        this.addedBy = addedBy;
    }

    public Project getProject() { return project; }
    public Location getLocation() { return location; }
    public BudgetCategory getCategory() { return category; }
    public String getLabel() { return label; }
    public BigDecimal getAmount() { return amount; }
    public BudgetStatus getStatus() { return status; }
    public String getNote() { return note; }
    public User getAddedBy() { return addedBy; }

    public void setLocation(Location location) { this.location = location; }
    public void setCategory(BudgetCategory category) { this.category = category; }
    public void setLabel(String label) { this.label = label; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setStatus(BudgetStatus status) { this.status = status; }
    public void setNote(String note) { this.note = note; }
}
