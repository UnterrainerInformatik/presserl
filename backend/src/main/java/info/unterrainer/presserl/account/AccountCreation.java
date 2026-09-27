package info.unterrainer.presserl.account;

import java.util.Comparator;
import java.util.List;

import org.jboss.logging.Logger;

import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionDelegation;
import info.unterrainer.presserl.section.SectionRoleDto;
import info.unterrainer.presserl.section.SectionRoleStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Creates an account with newspaper roles (Keycloak) and section roles (database), all or nothing:
 * every role is checked before Keycloak is called, and when storing the section roles fails, the
 * new Keycloak user is deleted again.
 */
@ApplicationScoped
public class AccountCreation {

    private static final Logger LOG = Logger.getLogger(AccountCreation.class);

    @Inject
    AccountService accounts;

    @Inject
    KeycloakCalls keycloakCalls;

    @Inject
    SectionRoleStore sectionRoles;

    /**
     * @throws AccountException {@code 403 roles} or {@code 403 sectionRoles} when {@code creator}
     *                          may not assign a role, {@code 400 sectionRoles} for an unknown
     *                          section, and everything {@link AccountService#create} throws
     */
    public Uni<CreatedAccountDto> create(Newsroom creator, CreateAccountRequest request) {
        AccountService.requireAssignable(creator.user(), request.roles());
        Uni<List<SectionRoleDto>> checked = request.sectionRoles().isEmpty()
                ? Uni.createFrom().item(List.of())
                : sectionRoles.sectionIds().map(ids -> checkSectionRoles(creator, request.sectionRoles(), ids));
        return checked.flatMap(ordered -> keycloakCalls.call(() -> accounts.create(creator.user(), request))
                .flatMap(created -> store(creator, created, ordered)))
                .invoke(created -> LOG.infof("Account '%s' created by '%s' with roles %s and section roles %s",
                        created.account().username(), creator.user().username(), created.account().roles(),
                        created.account().sectionRoles()));
    }

    private Uni<CreatedAccountDto> store(Newsroom creator, CreatedAccountDto created, List<SectionRoleDto> ordered) {
        if (ordered.isEmpty()) {
            return Uni.createFrom().item(created);
        }
        AccountDto account = created.account();
        return sectionRoles.insert(account.id(), ordered, creator.user().sub())
                .onFailure().call(e -> {
                    LOG.errorf(e, "Storing the section roles of account '%s' failed; deleting it", account.username());
                    return keycloakCalls.run(() -> accounts.deleteIncomplete(account.id(), account.username()));
                })
                .replaceWith(() -> new CreatedAccountDto(account.withSectionRoles(ordered), created.password()));
    }

    /**
     * The requested section roles ordered like {@code sectionIds} (by position).
     *
     * @throws AccountException {@code 400 sectionRoles} for a section not in {@code sectionIds},
     *                          {@code 403 sectionRoles} for a role outside the creator's scope
     */
    static List<SectionRoleDto> checkSectionRoles(Newsroom creator, List<SectionRoleDto> requested,
            List<Long> sectionIds) {
        List<Long> unknown = requested.stream().map(SectionRoleDto::sectionId)
                .filter(id -> !sectionIds.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw AccountException.invalid("sectionRoles", "unknown section " + unknown);
        }
        List<SectionRoleDto> refused = requested.stream()
                .filter(role -> !SectionDelegation.mayChange(creator, role.sectionId(), null, role.role())).toList();
        if (!refused.isEmpty()) {
            throw AccountException.forbidden("sectionRoles", "you may not assign " + refused);
        }
        return requested.stream().sorted(Comparator.comparingInt(role -> sectionIds.indexOf(role.sectionId())))
                .toList();
    }
}
