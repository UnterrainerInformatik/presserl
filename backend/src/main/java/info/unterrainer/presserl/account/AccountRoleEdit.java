package info.unterrainer.presserl.account;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jboss.logging.Logger;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionDelegation;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;
import info.unterrainer.presserl.section.SectionRoleStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Replaces the newspaper roles (Keycloak) and section roles (database) of an account, all or
 * nothing. Only the difference to the current roles is checked and written: every added or removed
 * role must be one the requesting user may assign, unchanged roles need no permission. Keycloak is
 * changed first; when storing the section roles fails, the Keycloak change is reverted.
 */
@ApplicationScoped
public class AccountRoleEdit {

    private static final Logger LOG = Logger.getLogger(AccountRoleEdit.class);

    @Inject
    AccountService accounts;

    @Inject
    KeycloakCalls keycloakCalls;

    @Inject
    SectionRoleStore sectionRoles;

    /**
     * @param target the account with its current newspaper and section roles, which
     *               {@code requester} may edit ({@link AccountAction#EDIT_ROLES})
     * @return the account with its new roles; {@code allowedActions} are left as they were
     * @throws AccountException {@code 400 sectionRoles} for an unknown section, {@code 403 roles} or
     *                          {@code 403 sectionRoles} for a change outside the requester's scope,
     *                          {@code 503} when a step fails
     */
    public Uni<AccountDto> edit(Newsroom requester, AccountDto target, EditRolesRequest request) {
        Uni<List<Long>> sectionIds = request.sectionRoles().isEmpty()
                ? Uni.createFrom().item(List.of())
                : sectionRoles.sectionIds();
        return sectionIds.flatMap(ids -> {
            Change change = check(requester, target, request, ids);
            if (change.isEmpty()) {
                return Uni.createFrom().item(target);
            }
            AccountDto edited = target.withRoles(request.roles()).withSectionRoles(change.sectionRoles());
            return write(requester, target, change)
                    .invoke(() -> LOG.infof("Roles of account '%s' changed by '%s': roles %s -> %s, "
                            + "section roles %s -> %s", target.username(), requester.user().username(),
                            target.roles(), edited.roles(), target.sectionRoles(), edited.sectionRoles()))
                    .replaceWith(edited);
        });
    }

    private Uni<Void> write(Newsroom requester, AccountDto target, Change change) {
        Uni<Void> groups = change.rolesChanged()
                ? keycloakCalls.run(() -> accounts.changeGroups(target.id(), change.added(), change.removed()))
                : Uni.createFrom().voidItem();
        if (!change.sectionRolesChanged()) {
            return groups;
        }
        return groups.flatMap(done -> sectionRoles.replace(target.id(), change.sectionRoles(), requester.user().sub())
                .onFailure().call(e -> {
                    LOG.errorf(e, "Storing the section roles of account '%s' failed; reverting its newspaper roles",
                            target.username());
                    return revertGroups(target, change);
                })
                .onFailure(e -> !(e instanceof AccountException)).transform(AccountException::unavailable));
    }

    private Uni<Void> revertGroups(AccountDto target, Change change) {
        if (!change.rolesChanged()) {
            return Uni.createFrom().voidItem();
        }
        return keycloakCalls.run(() -> accounts.changeGroups(target.id(), change.removed(), change.added()))
                .onFailure().recoverWithUni(e -> {
                    LOG.errorf(e, "Newspaper roles of account '%s' could not be restored to %s after a failed role "
                            + "edit; fix its groups in the Keycloak admin console", target.username(), target.roles());
                    return Uni.createFrom().voidItem();
                });
    }

    /**
     * The difference between the account's roles and the requested ones, once every part of it is
     * permitted.
     *
     * @param sectionIds the ids of all sections by position
     * @throws AccountException {@code 400 sectionRoles} for a section not in {@code sectionIds},
     *                          {@code 403 roles} for an added or removed newspaper role the requester
     *                          may not assign, {@code 403 sectionRoles} for a changed section outside
     *                          the requester's scope
     */
    static Change check(Newsroom requester, AccountDto target, EditRolesRequest request, List<Long> sectionIds) {
        List<Long> unknown = request.sectionRoles().stream().map(SectionRoleDto::sectionId)
                .filter(id -> !sectionIds.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw AccountException.invalid("sectionRoles", "unknown section " + unknown);
        }

        List<NewspaperRole> added = request.roles().stream().filter(role -> !target.roles().contains(role)).toList();
        List<NewspaperRole> removed = target.roles().stream().filter(role -> !request.roles().contains(role)).toList();
        List<NewspaperRole> assignable = RoleDelegation.assignableBy(requester.user());
        List<NewspaperRole> refusedRoles = new ArrayList<>(added);
        refusedRoles.addAll(removed);
        refusedRoles.removeAll(assignable);
        if (!refusedRoles.isEmpty()) {
            throw AccountException.forbidden("roles",
                    "you may not add or remove " + refusedRoles + "; assignable: " + assignable);
        }

        Map<Long, SectionRole> current = bySection(target.sectionRoles());
        Map<Long, SectionRole> requested = bySection(request.sectionRoles());
        Set<Long> sections = new LinkedHashSet<>(current.keySet());
        sections.addAll(requested.keySet());
        List<Long> changed = sections.stream()
                .filter(section -> !Objects.equals(current.get(section), requested.get(section))).toList();
        List<Long> refusedSections = changed.stream().filter(section -> {
            SectionRole next = requested.get(section);
            return next == null
                    ? !SectionDelegation.mayRemove(requester, section, current.get(section))
                    : !SectionDelegation.mayChange(requester, section, current.get(section), next);
        }).toList();
        if (!refusedSections.isEmpty()) {
            throw AccountException.forbidden("sectionRoles",
                    "you may not change the section roles in sections " + refusedSections);
        }

        List<SectionRoleDto> ordered = request.sectionRoles().stream()
                .sorted(Comparator.comparingInt(role -> sectionIds.indexOf(role.sectionId()))).toList();
        return new Change(added, removed, ordered, !changed.isEmpty());
    }

    private static Map<Long, SectionRole> bySection(List<SectionRoleDto> roles) {
        Map<Long, SectionRole> map = new HashMap<>();
        roles.forEach(role -> map.put(role.sectionId(), role.role()));
        return map;
    }

    /**
     * @param sectionRoles the requested section roles by section position
     */
    record Change(List<NewspaperRole> added, List<NewspaperRole> removed, List<SectionRoleDto> sectionRoles,
            boolean sectionRolesChanged) {

        boolean rolesChanged() {
            return !added.isEmpty() || !removed.isEmpty();
        }

        boolean isEmpty() {
            return !rolesChanged() && !sectionRolesChanged;
        }
    }
}
