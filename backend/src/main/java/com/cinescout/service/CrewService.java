package com.cinescout.service;

import com.cinescout.domain.ActivityTarget;
import com.cinescout.domain.ActivityVerb;
import com.cinescout.domain.DatabaseTime;
import com.cinescout.domain.Project;
import com.cinescout.domain.ProjectInvite;
import com.cinescout.domain.ProjectMember;
import com.cinescout.domain.ProjectRole;
import com.cinescout.domain.User;
import com.cinescout.dto.AddMemberRequest;
import com.cinescout.dto.AddMemberResponse;
import com.cinescout.dto.CrewResponse;
import com.cinescout.dto.InvitePreviewResponse;
import com.cinescout.dto.InviteResponse;
import com.cinescout.dto.MemberResponse;
import com.cinescout.persistence.BlockingTransactions;
import com.cinescout.repository.ProjectInviteRepository;
import com.cinescout.repository.ProjectMemberRepository;
import com.cinescout.repository.ProjectRepository;
import com.cinescout.repository.UserRepository;
import com.cinescout.security.SecretTokens;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A project's crew: who is on it and in what role, invitations for people without an account, handing ownership
 * over, and leaving. Everyone on the crew can see who else is; only the owner changes it (a member may leave).
 */
@Service
public class CrewService {

    static final Duration INVITE_LIFETIME = Duration.ofDays(7);

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectInviteRepository invites;
    private final UserRepository users;
    private final ProjectAccess access;
    private final BlockingTransactions db;
    private final ActivityLog activity;

    public CrewService(ProjectRepository projects, ProjectMemberRepository members, ProjectInviteRepository invites,
                       UserRepository users, ProjectAccess access, BlockingTransactions db, ActivityLog activity) {
        this.activity = activity;
        this.projects = projects;
        this.members = members;
        this.invites = invites;
        this.users = users;
        this.access = access;
        this.db = db;
    }

    public Mono<CrewResponse> crew(UUID userId, UUID projectId) {
        return db.call(() -> {
            access.project(userId, projectId, ProjectRole.VIEWER);
            List<MemberResponse> crew = members.findCrew(projectId).stream().map(MemberResponse::from).toList();
            List<InviteResponse> open = access.role(userId, projectId) == ProjectRole.OWNER
                    ? invites.findOpen(projectId, DatabaseTime.now()).stream().map(i -> InviteResponse.from(i, null)).toList()
                    : List.of();
            return new CrewResponse(crew, open);
        });
    }

    /**
     * Brings someone onto the crew. An account with that email joins at once; for anyone else an invite is made (any
     * earlier open invite to the same address is withdrawn) and its link comes back once, for the owner to pass on.
     */
    public Mono<AddMemberResponse> add(UUID userId, UUID projectId, AddMemberRequest request) {
        return db.call(() -> {
            Project project = access.project(userId, projectId, ProjectRole.OWNER);
            requireCrewRole(request.role());
            String email = request.email().strip();
            User inviter = users.getReferenceById(userId);
            User existing = users.findByEmailIgnoreCase(email).orElse(null);
            if (existing != null) {
                if (members.existsByProjectIdAndUserId(projectId, existing.getId())) {
                    throw new ConflictException(existing.getDisplayName() + " is already on this project's crew");
                }
                ProjectMember member = members.saveAndFlush(new ProjectMember(project, existing, request.role(), inviter));
                activity.record(project, userId, ActivityVerb.MEMBER_JOINED, ActivityTarget.MEMBER, existing.getId(),
                        ActivityLog.facts("member", existing.getDisplayName(), "role", request.role().name()));
                return new AddMemberResponse(MemberResponse.from(member), null);
            }
            Instant now = DatabaseTime.now();
            invites.findOpen(projectId, now).stream()
                    .filter(open -> open.getEmail().equalsIgnoreCase(email))
                    .forEach(invites::delete);
            String token = SecretTokens.newToken();
            ProjectInvite invite = invites.saveAndFlush(new ProjectInvite(project, email.toLowerCase(Locale.ROOT), request.role(),
                    SecretTokens.hash(token), inviter, now.plus(INVITE_LIFETIME)));
            return new AddMemberResponse(null, InviteResponse.from(invite, token));
        });
    }

    /** Withdraws an invite that has not been taken up; its link stops working. */
    public Mono<Void> revokeInvite(UUID userId, UUID projectId, UUID inviteId) {
        return db.run(() -> {
            access.project(userId, projectId, ProjectRole.OWNER);
            ProjectInvite invite = invites.findByIdAndProjectId(inviteId, projectId)
                    .filter(i -> i.getAcceptedAt() == null)
                    .orElseThrow(() -> new NotFoundException("Invite", inviteId));
            invites.delete(invite);
        });
    }

    public Mono<MemberResponse> changeRole(UUID userId, UUID projectId, UUID memberId, ProjectRole role) {
        return db.call(() -> {
            access.project(userId, projectId, ProjectRole.OWNER);
            requireCrewRole(role);
            ProjectMember member = member(projectId, memberId);
            if (member.getRole() == ProjectRole.OWNER) {
                throw new ConflictException("The owner's role changes only by handing ownership to someone else");
            }
            if (member.getRole() != role) {
                activity.record(member.getProject(), userId, ActivityVerb.ROLE_CHANGED, ActivityTarget.MEMBER, memberId,
                        ActivityLog.facts("member", member.getUser().getDisplayName(), "from", member.getRole().name(), "to", role.name()));
            }
            member.setRole(role);
            return MemberResponse.from(members.saveAndFlush(member));
        });
    }

    /** The owner removes a member, or a member leaves. The owner cannot leave without handing ownership over first. */
    public Mono<Void> remove(UUID userId, UUID projectId, UUID memberId) {
        return db.run(() -> {
            access.project(userId, projectId, userId.equals(memberId) ? ProjectRole.VIEWER : ProjectRole.OWNER);
            ProjectMember member = member(projectId, memberId);
            if (member.getRole() == ProjectRole.OWNER) {
                throw new ConflictException("The owner cannot leave; hand ownership to another member first");
            }
            boolean leaving = userId.equals(memberId);
            activity.record(member.getProject(), userId, leaving ? ActivityVerb.MEMBER_LEFT : ActivityVerb.MEMBER_REMOVED,
                    ActivityTarget.MEMBER, memberId, ActivityLog.facts("member", member.getUser().getDisplayName()));
            members.delete(member);
        });
    }

    /** Makes another member the owner; the current owner stays on as an EDITOR. */
    public Mono<CrewResponse> transfer(UUID userId, UUID projectId, UUID newOwnerId) {
        return db.call(() -> {
            Project project = access.project(userId, projectId, ProjectRole.OWNER);
            if (userId.equals(newOwnerId)) {
                throw new InvalidRequestException("userId", "You already own this project");
            }
            ProjectMember next = member(projectId, newOwnerId);
            ProjectMember current = member(projectId, userId);
            // One owner at a time (uq_project_members_owner): step down before the new owner steps up.
            current.setRole(ProjectRole.EDITOR);
            members.saveAndFlush(current);
            next.setRole(ProjectRole.OWNER);
            members.saveAndFlush(next);
            project.setOwner(next.getUser());
            activity.record(project, userId, ActivityVerb.OWNERSHIP_TRANSFERRED, ActivityTarget.MEMBER, newOwnerId,
                    ActivityLog.facts("member", next.getUser().getDisplayName()));
            projects.saveAndFlush(project);
            return new CrewResponse(members.findCrew(projectId).stream().map(MemberResponse::from).toList(), List.of());
        });
    }

    /** What an invite link offers, for the page that asks the user to accept it. */
    public Mono<InvitePreviewResponse> preview(String token) {
        return db.call(() -> preview(invite(token)));
    }

    /**
     * Joins the project the invite is for. The invite is for one email address: an account with another address
     * cannot use it. Someone already on the crew just uses up the invite.
     */
    public Mono<InvitePreviewResponse> accept(UUID userId, String token) {
        return db.call(() -> {
            ProjectInvite invite = invite(token);
            Instant now = DatabaseTime.now();
            if (!invite.isOpen(now)) {
                throw new ConflictException(invite.getAcceptedAt() != null
                        ? "This invite has already been used" : "This invite has expired; ask for a new one");
            }
            User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
            if (!user.getEmail().equalsIgnoreCase(invite.getEmail())) {
                throw new ForbiddenException("This invite is for " + invite.getEmail() + "; log in with that address to accept it");
            }
            Project project = invite.getProject();
            if (!members.existsByProjectIdAndUserId(project.getId(), userId)) {
                members.saveAndFlush(new ProjectMember(project, user, invite.getRole(), invite.getInvitedBy()));
                activity.record(project, userId, ActivityVerb.MEMBER_JOINED, ActivityTarget.MEMBER, userId,
                        ActivityLog.facts("member", user.getDisplayName(), "role", invite.getRole().name(), "byInvite", true));
            }
            invite.accept(user, now);
            return preview(invites.saveAndFlush(invite));
        });
    }

    private ProjectInvite invite(String token) {
        if (!SecretTokens.wellFormed(token)) {
            throw new NotFoundException("This invite link is not valid");
        }
        return invites.findByTokenHash(SecretTokens.hash(token)).orElseThrow(() -> new NotFoundException("This invite link is not valid"));
    }

    private static InvitePreviewResponse preview(ProjectInvite invite) {
        String state = invite.getAcceptedAt() != null ? "ACCEPTED" : invite.isOpen(DatabaseTime.now()) ? "OPEN" : "EXPIRED";
        User inviter = invite.getInvitedBy();
        return new InvitePreviewResponse(invite.getProject().getId(), invite.getProject().getTitle(),
                inviter == null ? null : inviter.getDisplayName(), invite.getEmail(), invite.getRole(), invite.getExpiresAt(), state);
    }

    private ProjectMember member(UUID projectId, UUID memberId) {
        return members.findMember(projectId, memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
    }

    private static void requireCrewRole(ProjectRole role) {
        if (role == ProjectRole.OWNER) {
            throw new InvalidRequestException("role", "A project has one owner; hand ownership over instead");
        }
    }
}
