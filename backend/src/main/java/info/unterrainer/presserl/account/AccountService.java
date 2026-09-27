package info.unterrainer.presserl.account;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import info.unterrainer.presserl.bootstrap.NewspaperGroups;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Newspaper accounts on top of the Keycloak Admin API: Keycloak users are the accounts, the
 * groups of the {@link NewspaperRole}s their newspaper-wide roles. Section roles live in the
 * Presserl database and are added by the callers ({@link AccountCreation}). Blocking — call it on a
 * worker thread ({@link KeycloakCalls}).
 */
@ApplicationScoped
public class AccountService {

    static final int LIST_MAX = 1000;

    private static final Logger LOG = Logger.getLogger(AccountService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> NAME_FIELDS = Set.of("username", "firstName", "lastName");

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakRealm keycloakRealm;

    @Inject
    PassPhraseGenerator passPhrases;

    /**
     * Every account except service accounts, sorted by username, without section roles; at most
     * {@value #LIST_MAX}.
     */
    public List<AccountDto> list() {
        return keycloakCall(() -> {
            RealmResource realm = realm();
            Map<String, EnumSet<NewspaperRole>> roles = new HashMap<>();
            groupIds(realm).forEach((role, groupId) -> realm.groups().group(groupId).members(0, LIST_MAX, true)
                    .forEach(member -> roles.computeIfAbsent(member.getId(), id -> EnumSet.noneOf(NewspaperRole.class))
                            .add(role)));
            return realm.users().list(0, LIST_MAX).stream()
                    .filter(user -> !isServiceAccount(user))
                    .map(user -> account(user, List.copyOf(roles.getOrDefault(user.getId(),
                            EnumSet.noneOf(NewspaperRole.class)))))
                    .sorted(Comparator.comparing(AccountDto::username))
                    .toList();
        });
    }

    /**
     * The ids of the members of each role's group, locked accounts included; at most
     * {@value #LIST_MAX} per role.
     */
    public Map<NewspaperRole, Set<String>> memberIds(Set<NewspaperRole> roles) {
        return keycloakCall(() -> {
            RealmResource realm = realm();
            Map<NewspaperRole, String> groupIds = groupIds(realm);
            Map<NewspaperRole, Set<String>> members = new EnumMap<>(NewspaperRole.class);
            roles.forEach(role -> members.put(role, realm.groups().group(groupIds.get(role))
                    .members(0, LIST_MAX, true).stream()
                    .map(UserRepresentation::getId)
                    .collect(Collectors.toUnmodifiableSet())));
            return members;
        });
    }

    /**
     * The first free username derived from {@code firstName} (see {@link UsernameDeriver}).
     */
    public String suggestUsername(String firstName) {
        return keycloakCall(() -> {
            RealmResource realm = realm();
            return UsernameDeriver.firstFree(UsernameDeriver.base(firstName), candidate -> taken(realm, candidate));
        });
    }

    /**
     * The account with this id and its newspaper roles, without section roles; empty when there is
     * none or it is a service account.
     */
    public Optional<AccountDto> find(String id) {
        return keycloakCall(() -> {
            try {
                UserResource resource = realm().users().get(id);
                UserRepresentation user = resource.toRepresentation();
                if (isServiceAccount(user)) {
                    return Optional.empty();
                }
                List<String> groups = resource.groups().stream().map(GroupRepresentation::getName).toList();
                return Optional.of(account(user, NewspaperRole.fromGroups(groups)));
            } catch (WebApplicationException e) {
                Response response = e.getResponse();
                if (response != null && response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                    return Optional.empty();
                }
                throw e;
            }
        });
    }

    /**
     * Creates an enabled account with a generated password and joins the groups of its roles. All
     * or nothing: when a step after the creation fails, the new user is deleted again. Section
     * roles are not stored here; the returned account has none.
     *
     * @throws AccountException {@code 403 roles} when {@code creator} may not assign a role,
     *                          {@code 409 username} when the username is taken, {@code 400} when
     *                          Keycloak rejects a name, {@code 503} when Keycloak is unavailable
     */
    public CreatedAccountDto create(CurrentUser creator, CreateAccountRequest request) {
        requireAssignable(creator, request.roles());
        String password = passPhrases.generate();
        AccountDto account = keycloakCall(() -> {
            RealmResource realm = realm();
            Map<NewspaperRole, String> groupIds = groupIds(realm);
            if (taken(realm, request.username())) {
                throw usernameTaken();
            }
            String id = createUser(realm, request, password);
            try {
                request.roles().forEach(role -> realm.users().get(id).joinGroup(groupIds.get(role)));
            } catch (RuntimeException e) {
                deleteOrphan(realm, id, request.username());
                throw e;
            }
            return new AccountDto(id, request.username(), request.firstName(), request.lastName(), request.roles(),
                    List.of(), true, List.of());
        });
        return new CreatedAccountDto(account, password);
    }

    /**
     * Sets {@code password} as the account's permanent password.
     */
    public void resetPassword(String id, String password) {
        keycloakCall(() -> {
            realm().users().get(id).resetPassword(passwordCredential(password));
            return null;
        });
    }

    /**
     * Enables or disables the account; no Keycloak write when it already is in that state.
     *
     * @return whether the state changed
     */
    public boolean setEnabled(String id, boolean enabled) {
        return keycloakCall(() -> {
            UserResource resource = realm().users().get(id);
            UserRepresentation user = resource.toRepresentation();
            if (Boolean.TRUE.equals(user.isEnabled()) == enabled) {
                return false;
            }
            user.setEnabled(enabled);
            resource.update(user);
            return true;
        });
    }

    /**
     * Ends every Keycloak session of the account, so its refresh tokens stop working.
     */
    public void logout(String id) {
        keycloakCall(() -> {
            realm().users().get(id).logout();
            return null;
        });
    }

    /**
     * Joins the groups of {@code added} and leaves those of {@code removed}. All or nothing: when a
     * step fails, the steps already done are reverted (a failing revert is logged as an error) and
     * the failure is rethrown.
     *
     * @throws AccountException {@code 503} when Keycloak is unavailable
     */
    public void changeGroups(String id, List<NewspaperRole> added, List<NewspaperRole> removed) {
        keycloakCall(() -> {
            RealmResource realm = realm();
            Map<NewspaperRole, String> groupIds = groupIds(realm);
            UserResource user = realm.users().get(id);
            List<Runnable> undo = new ArrayList<>();
            try {
                for (NewspaperRole role : added) {
                    user.joinGroup(groupIds.get(role));
                    undo.addFirst(() -> user.leaveGroup(groupIds.get(role)));
                }
                for (NewspaperRole role : removed) {
                    user.leaveGroup(groupIds.get(role));
                    undo.addFirst(() -> user.joinGroup(groupIds.get(role)));
                }
            } catch (RuntimeException e) {
                revert(id, undo, added, removed);
                throw e;
            }
            return null;
        });
    }

    private static void revert(String id, List<Runnable> undo, List<NewspaperRole> added,
            List<NewspaperRole> removed) {
        try {
            undo.forEach(Runnable::run);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Changing the groups of account %s (join %s, leave %s) failed and could not be reverted; "
                    + "check its groups in the Keycloak admin console", id, added, removed);
        }
    }

    /**
     * Deletes an account whose creation could not be completed; failures are logged, not thrown.
     */
    public void deleteIncomplete(String id, String username) {
        try {
            deleteOrphan(realm(), id, username);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Account '%s' (%s) was created incompletely and could not be deleted; "
                    + "remove it in the Keycloak admin console", username, id);
        }
    }

    /**
     * @throws AccountException {@code 403 roles} when {@code creator} may not assign one of
     *                          {@code roles}
     */
    static void requireAssignable(CurrentUser creator, List<NewspaperRole> roles) {
        List<NewspaperRole> assignable = RoleDelegation.assignableBy(creator);
        List<NewspaperRole> refused = roles.stream().filter(role -> !assignable.contains(role)).toList();
        if (!refused.isEmpty()) {
            throw AccountException.forbidden("roles", "you may not assign " + refused + "; assignable: " + assignable);
        }
    }

    /**
     * The realm the backend's service account manages; overridden by unit tests.
     */
    RealmResource realm() {
        return keycloak.realm(keycloakRealm.name());
    }

    private String createUser(RealmResource realm, CreateAccountRequest request, String password) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.username());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName().isEmpty() ? null : request.lastName());
        user.setEnabled(true);
        user.setCredentials(List.of(passwordCredential(password)));

        try (Response response = realm.users().create(user)) {
            int status = response.getStatus();
            if (status == Response.Status.CREATED.getStatusCode()) {
                String location = response.getLocation().getPath();
                return location.substring(location.lastIndexOf('/') + 1);
            }
            if (status == Response.Status.CONFLICT.getStatusCode()) {
                throw usernameTaken();
            }
            String body = response.hasEntity() ? response.readEntity(String.class) : "";
            if (status == Response.Status.BAD_REQUEST.getStatusCode()) {
                List<FieldError> errors = rejectedFields(body);
                if (!errors.isEmpty()) {
                    throw AccountException.invalid(errors);
                }
            }
            throw new WebApplicationException("Creating user '%s' failed with HTTP %d: %s"
                    .formatted(request.username(), status, body), status);
        }
    }

    private static CredentialRepresentation passwordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }

    private void deleteOrphan(RealmResource realm, String id, String username) {
        try (Response response = realm.users().delete(id)) {
            if (response.getStatus() >= 300) {
                LOG.errorf("Account '%s' (%s) was created incompletely and could not be deleted (HTTP %d); "
                        + "remove it in the Keycloak admin console", username, id, response.getStatus());
            }
        } catch (RuntimeException e) {
            LOG.errorf(e, "Account '%s' (%s) was created incompletely and could not be deleted; "
                    + "remove it in the Keycloak admin console", username, id);
        }
    }

    private Map<NewspaperRole, String> groupIds(RealmResource realm) {
        Map<NewspaperRole, String> ids = new EnumMap<>(NewspaperRole.class);
        for (NewspaperRole role : NewspaperRole.values()) {
            ids.put(role, NewspaperGroups.id(realm, role).orElseThrow(() -> new IllegalStateException(
                    "Keycloak realm '%s' has no group '%s' - import the realm template (deploy/keycloak/presserl-realm.json)"
                            .formatted(keycloakRealm.name(), role.group()))));
        }
        return ids;
    }

    private static boolean taken(RealmResource realm, String username) {
        return !realm.users().searchByUsername(username, true).isEmpty();
    }

    private static boolean isServiceAccount(UserRepresentation user) {
        return user.getServiceAccountClientId() != null
                || user.getUsername().startsWith(AccountRequestValidator.SERVICE_ACCOUNT_PREFIX);
    }

    private static AccountDto account(UserRepresentation user, List<NewspaperRole> roles) {
        return new AccountDto(user.getId(), user.getUsername(), orEmpty(user.getFirstName()),
                orEmpty(user.getLastName()), roles, List.of(), Boolean.TRUE.equals(user.isEnabled()), List.of());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static AccountException usernameTaken() {
        return AccountException.conflict("username", "is already taken");
    }

    /**
     * Keycloak's user-profile errors ({@code {"field": ..., "errorMessage": ...}}, possibly wrapped
     * in {@code {"errors": [...]}}) for the name fields; empty when the body names none.
     */
    static List<FieldError> rejectedFields(String body) {
        List<FieldError> errors = new ArrayList<>();
        try {
            JsonNode json = JSON.readTree(body);
            JsonNode items = json != null && json.has("errors") ? json.get("errors") : json;
            for (JsonNode item : items != null && items.isArray() ? items : List.of(items)) {
                String field = item == null ? null : item.path("field").asText(null);
                if (field != null && NAME_FIELDS.contains(field)) {
                    errors.add(new FieldError(field, "rejected by Keycloak: " + item.path("errorMessage").asText()));
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return errors;
    }

    /**
     * Runs Keycloak calls; failures to reach Keycloak or refusals of the service account become
     * {@code 503}, refusals decided here pass through.
     */
    private static <T> T keycloakCall(Supplier<T> call) {
        try {
            return call.get();
        } catch (AccountException e) {
            throw e;
        } catch (ProcessingException | WebApplicationException | IllegalStateException e) {
            LOG.warnf(e, "Keycloak account request failed");
            throw AccountException.unavailable(e);
        }
    }
}
