package info.unterrainer.presserl.section;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import info.unterrainer.presserl.account.AccountDto;
import info.unterrainer.presserl.account.AccountService;
import info.unterrainer.presserl.account.KeycloakCalls;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

/**
 * The members of a section and their section roles, managed by users who may assign section roles
 * there ({@link SectionDelegation}). Accounts are Keycloak users; members whose user no longer
 * exists are left out.
 */
@ApplicationScoped
public class SectionMembers {

    private static final Logger LOG = Logger.getLogger(SectionMembers.class);
    private static final Comparator<MemberDto> ORDER = Comparator
            .comparing((MemberDto member) -> member.role() != SectionRole.SECTION_EDITOR)
            .thenComparing(MemberDto::username);

    @Inject
    SectionService sections;

    @Inject
    SectionRoleStore sectionRoles;

    @Inject
    AccountService accounts;

    @Inject
    KeycloakCalls keycloakCalls;

    @Inject
    LastSectionRule lastSectionRule;

    /**
     * @throws NotFoundException  for an unknown section
     * @throws ForbiddenException when the user may not manage the section's members
     */
    public Uni<MemberListDto> list(Newsroom newsroom, long sectionId) {
        return manageable(newsroom, sectionId).flatMap(section -> sectionRoles.members(sectionId)
                .flatMap(roles -> keycloakCalls.call(() -> members(roles)))
                .map(members -> new MemberListDto(SectionDelegation.assignable(newsroom, sectionId), members)));
    }

    /**
     * Gives the account {@code role} in the section, replacing its current role.
     *
     * @throws NotFoundException  for an unknown section or account
     * @throws ForbiddenException when the user may not manage the section's members
     * @throws SectionException   {@code 403 role} when the user may not make this change
     */
    public Uni<MemberDto> assign(Newsroom newsroom, long sectionId, String accountId, SectionRole role) {
        return manageable(newsroom, sectionId).flatMap(section -> account(accountId)
                .flatMap(account -> sectionRoles.roleOf(sectionId, accountId).flatMap(current -> {
                    if (!SectionDelegation.mayChange(newsroom, sectionId, current, role)) {
                        throw SectionException.forbidden("role", "you may not change " + current + " to " + role
                                + " in this section");
                    }
                    return sectionRoles.put(sectionId, accountId, role, newsroom.user().sub())
                            .invoke(() -> LOG.infof("Section role of '%s' in section '%s' set to %s (was %s) by '%s'",
                                    account.username(), section.name, role, current, newsroom.user().username()))
                            .replaceWith(new MemberDto(account.id(), account.username(), account.firstName(),
                                    account.lastName(), role));
                })));
    }

    /**
     * Removes the account's role in the section; an account left without any section role becomes a
     * sectionless reporter ({@link LastSectionRule}) in the same transaction.
     *
     * @throws NotFoundException  for an unknown section, an account without a role there or an
     *                            unknown account
     * @throws ForbiddenException when the user may not manage the section's members
     * @throws SectionException   {@code 403 role} when the user may not remove the account's role
     */
    public Uni<Void> remove(Newsroom newsroom, long sectionId, String accountId) {
        return manageable(newsroom, sectionId).flatMap(section -> sectionRoles.roleOf(sectionId, accountId)
                .onItem().ifNull().failWith(NotFoundException::new)
                .flatMap(current -> account(accountId).flatMap(account -> {
                    if (!SectionDelegation.mayRemove(newsroom, sectionId, current)) {
                        throw SectionException.forbidden("role", "you may not remove " + current
                                + " in this section");
                    }
                    return Panache.withTransaction(() -> sectionRoles.remove(sectionId, accountId)
                            .call(() -> lastSectionRule.apply(List.of(accountId), newsroom.user())))
                            .invoke(() -> LOG.infof("Section role %s of '%s' in section '%s' removed by '%s'",
                                    current, account.username(), section.name, newsroom.user().username()))
                            .replaceWithVoid();
                })));
    }

    private Uni<SectionEntity> manageable(Newsroom newsroom, long sectionId) {
        return sections.get(sectionId).invoke(section -> {
            if (SectionDelegation.assignable(newsroom, sectionId).isEmpty()) {
                throw new ForbiddenException();
            }
        });
    }

    private Uni<AccountDto> account(String accountId) {
        return keycloakCalls.call(() -> accounts.find(accountId))
                .map(account -> account.orElseThrow(NotFoundException::new));
    }

    /**
     * Blocking: looks up every member in Keycloak.
     */
    private List<MemberDto> members(Map<String, SectionRole> roles) {
        return roles.entrySet().stream()
                .map(entry -> accounts.find(entry.getKey()).map(account -> new MemberDto(account.id(),
                        account.username(), account.firstName(), account.lastName(), entry.getValue())))
                .flatMap(Optional::stream)
                .sorted(ORDER)
                .toList();
    }
}
