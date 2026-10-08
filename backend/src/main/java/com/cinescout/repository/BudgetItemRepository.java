package com.cinescout.repository;

import com.cinescout.domain.BudgetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetItemRepository extends JpaRepository<BudgetItem, UUID> {

    /** A project's lines, each with its venue and scene, in the order they were added. Access is checked on the project first. */
    @Query("""
            select b from BudgetItem b left join fetch b.location l left join fetch l.scene left join fetch b.addedBy
            where b.project.id = :projectId order by b.createdAt asc, b.id asc""")
    List<BudgetItem> findByProject(@Param("projectId") UUID projectId);

    @Query("select count(b) from BudgetItem b where b.project.id = :projectId")
    long countByProject(@Param("projectId") UUID projectId);

    /** A line with its project, for checking the user's role on it. */
    @Query("select b from BudgetItem b join fetch b.project left join fetch b.location l left join fetch l.scene left join fetch b.addedBy where b.id = :id")
    Optional<BudgetItem> findWithProject(@Param("id") UUID id);
}
