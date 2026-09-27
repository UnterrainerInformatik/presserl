package info.unterrainer.presserl.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.auth.CurrentUser;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.bootstrap.KeycloakAdminProducer.KeycloakRealm;
import info.unterrainer.presserl.section.Newsroom;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;
import info.unterrainer.presserl.section.SectionRoleStore;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * {@link AccountService}, {@link AccountCreation} and {@link AccountRoleEdit} against a stubbed
 * Keycloak realm: compensation and error mapping.
 */
class AccountServiceTest {

    private static final CurrentUser PUBLISHER = new CurrentUser("sub", "publisher", "Publisher",
            List.of(NewspaperRole.PUBLISHER));
    private static final CreateAccountRequest LENA = new CreateAccountRequest("Lena", "", "lena",
            List.of(NewspaperRole.EDITOR_IN_CHIEF), List.of());

    private final List<String> deleted = new ArrayList<>();

    @Test
    void failedGroupJoinDeletesTheNewUser() {
        UserResource user = stub(UserResource.class, Map.of("joinGroup", args -> {
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }));
        AccountService service = service(users(Response.created(URI.create("http://kc/users/id-1")).build(), user));

        assertThatThrownBy(() -> service.create(PUBLISHER, LENA))
                .isInstanceOfSatisfying(AccountException.class,
                        e -> assertThat(e.status()).isEqualTo(Status.SERVICE_UNAVAILABLE));
        assertThat(deleted).containsExactly("id-1");
    }

    @Test
    void failedSectionRoleInsertDeletesTheNewUser() {
        AccountCreation creation = new AccountCreation();
        creation.accounts = service(users(Response.created(URI.create("http://kc/users/id-2")).build(), null));
        creation.keycloakCalls = new KeycloakCalls() {
            @Override
            public <T> Uni<T> call(Supplier<T> blocking) {
                return Uni.createFrom().item(blocking);
            }
        };
        creation.sectionRoles = new SectionRoleStore() {
            @Override
            public Uni<List<Long>> sectionIds() {
                return Uni.createFrom().item(List.of(7L));
            }

            @Override
            public Uni<Void> insert(String accountId, List<SectionRoleDto> roles, String assignedBy) {
                return Uni.createFrom().failure(new IllegalStateException("database down"));
            }
        };
        CreateAccountRequest max = new CreateAccountRequest("Max", "", "max", List.of(),
                List.of(new SectionRoleDto(7L, SectionRole.REPORTER)));

        assertThatThrownBy(() -> creation.create(new Newsroom(PUBLISHER, Map.of()), max).await().indefinitely())
                .hasMessageContaining("database down");
        assertThat(deleted).containsExactly("id-2");
    }

    @Test
    void failedSectionRoleReplaceRestoresTheGroups() {
        List<String> calls = new ArrayList<>();
        AccountRoleEdit edit = new AccountRoleEdit();
        edit.accounts = service(users(null, groupRecorder(calls, null)));
        edit.keycloakCalls = new KeycloakCalls() {
            @Override
            public <T> Uni<T> call(Supplier<T> blocking) {
                return Uni.createFrom().item(blocking);
            }
        };
        edit.sectionRoles = new SectionRoleStore() {
            @Override
            public Uni<List<Long>> sectionIds() {
                return Uni.createFrom().item(List.of(7L));
            }

            @Override
            public Uni<Void> replace(String accountId, List<SectionRoleDto> requested, String assignedBy) {
                return Uni.createFrom().failure(new IllegalStateException("database down"));
            }
        };
        AccountDto reader = new AccountDto("id-6", "reader", "Reader", "", List.of(NewspaperRole.READER), List.of(),
                true, List.of(), List.of(), List.of());
        EditRolesRequest request = new EditRolesRequest(List.of(NewspaperRole.EDITOR_IN_CHIEF),
                List.of(new SectionRoleDto(7L, SectionRole.REPORTER)));

        assertThatThrownBy(() -> edit.edit(new Newsroom(PUBLISHER, Map.of()), reader, request).await().indefinitely())
                .isInstanceOfSatisfying(AccountException.class,
                        e -> assertThat(e.status()).isEqualTo(Status.SERVICE_UNAVAILABLE));
        assertThat(calls).containsExactly("join group-editor-in-chief", "leave group-reader",
                "join group-reader", "leave group-editor-in-chief");
    }

    @Test
    void changeGroupsJoinsAndLeaves() {
        List<String> calls = new ArrayList<>();
        AccountService service = service(users(null, groupRecorder(calls, null)));

        service.changeGroups("id-3", List.of(NewspaperRole.EDITOR_IN_CHIEF), List.of(NewspaperRole.READER));

        assertThat(calls).containsExactly("join group-editor-in-chief", "leave group-reader");
    }

    @Test
    void failedGroupStepRevertsTheStepsDone() {
        List<String> calls = new ArrayList<>();
        AccountService service = service(users(null, groupRecorder(calls, "leave group-reader")));

        assertThatThrownBy(() -> service.changeGroups("id-4",
                List.of(NewspaperRole.PUBLISHER, NewspaperRole.EDITOR_IN_CHIEF), List.of(NewspaperRole.READER)))
                .isInstanceOfSatisfying(AccountException.class,
                        e -> assertThat(e.status()).isEqualTo(Status.SERVICE_UNAVAILABLE));
        assertThat(calls).containsExactly("join group-publisher", "join group-editor-in-chief", "leave group-reader",
                "leave group-editor-in-chief", "leave group-publisher");
    }

    @Test
    void failedRevertStillRethrowsTheOriginalFailure() {
        List<String> calls = new ArrayList<>();
        UserResource user = stub(UserResource.class, Map.of(
                "joinGroup", args -> {
                    calls.add("join " + args[0]);
                    return null;
                },
                "leaveGroup", args -> {
                    calls.add("leave " + args[0]);
                    throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
                }));
        AccountService service = service(users(null, user));

        assertThatThrownBy(() -> service.changeGroups("id-5", List.of(NewspaperRole.EDITOR_IN_CHIEF),
                List.of(NewspaperRole.READER))).isInstanceOfSatisfying(AccountException.class,
                        e -> assertThat(e.status()).isEqualTo(Status.SERVICE_UNAVAILABLE));
        assertThat(calls).containsExactly("join group-editor-in-chief", "leave group-reader",
                "leave group-editor-in-chief");
    }

    @Test
    void keycloakConflictOnCreateIsUsernameConflict() {
        AccountService service = service(users(Response.status(Status.CONFLICT).build(), null));

        assertThatThrownBy(() -> service.create(PUBLISHER, LENA))
                .isInstanceOfSatisfying(AccountException.class, e -> {
                    assertThat(e.status()).isEqualTo(Status.CONFLICT);
                    assertThat(e.errors()).extracting(FieldError::field).containsExactly("username");
                });
        assertThat(deleted).isEmpty();
    }

    @Test
    void keycloakNameRejectionIsBadRequestAtTheField() {
        Response rejected = Response.status(Status.BAD_REQUEST)
                .entity("{\"field\":\"firstName\",\"errorMessage\":\"error-person-name-invalid-character\"}")
                .build();
        AccountService service = service(users(rejected, null));

        assertThatThrownBy(() -> service.create(PUBLISHER, LENA))
                .isInstanceOfSatisfying(AccountException.class, e -> {
                    assertThat(e.status()).isEqualTo(Status.BAD_REQUEST);
                    assertThat(e.errors()).extracting(FieldError::field).containsExactly("firstName");
                });
    }

    @Test
    void unreachableKeycloakIsUnavailable() {
        Function<Object[], Object> refused = args -> {
            throw new ProcessingException("connection refused");
        };
        AccountService service = service(stub(RealmResource.class, Map.of("groups", refused, "users", refused)));

        assertThatThrownBy(service::list).isInstanceOfSatisfying(AccountException.class, e -> {
            assertThat(e.status()).isEqualTo(Status.SERVICE_UNAVAILABLE);
            assertThat(e.errors()).extracting(FieldError::field).containsOnlyNulls();
        });
        assertThatThrownBy(() -> service.suggestUsername("Anna")).isInstanceOf(AccountException.class);
    }

    @Test
    void refusedServiceAccountIsUnavailable() {
        AccountService service = service(stub(RealmResource.class, Map.of("users", args -> {
            throw new WebApplicationException(Status.FORBIDDEN);
        })));

        assertThatThrownBy(() -> service.suggestUsername("Anna")).isInstanceOfSatisfying(AccountException.class,
                e -> assertThat(e.status()).isEqualTo(Status.SERVICE_UNAVAILABLE));
    }

    @Test
    void delegationIsCheckedBeforeKeycloakIsCalled() {
        AccountService service = service(stub(RealmResource.class, Map.of()));
        CurrentUser chief = new CurrentUser("sub", "chief", "Chief", List.of(NewspaperRole.EDITOR_IN_CHIEF));
        CreateAccountRequest boss = new CreateAccountRequest("Boss", "", "boss", List.of(NewspaperRole.PUBLISHER),
                List.of());

        assertThatThrownBy(() -> service.create(chief, boss)).isInstanceOfSatisfying(AccountException.class, e -> {
            assertThat(e.status()).isEqualTo(Status.FORBIDDEN);
            assertThat(e.errors()).extracting(FieldError::field).containsExactly("roles");
        });
    }

    @Test
    void createdAccountHidesThePasswordInToString() {
        CreatedAccountDto created = new CreatedAccountDto(
                new AccountDto("id", "lena", "Lena", "", List.of(NewspaperRole.READER), List.of(), true, List.of(), List.of(),
                        List.of()), "tiger-wolke-apfel-leiter");

        assertThat(created.toString()).contains("lena").doesNotContain("tiger");
    }

    /**
     * A realm with the three newspaper groups and the given users resource.
     */
    private RealmResource users(Response createResponse, UserResource user) {
        GroupsResource groups = stub(GroupsResource.class, Map.of("groups", args -> {
            GroupRepresentation group = new GroupRepresentation();
            group.setName((String) args[0]);
            group.setId("group-" + args[0]);
            return List.of(group);
        }, "group", args -> stub(GroupResource.class, Map.of("members", a -> List.of()))));
        UsersResource users = stub(UsersResource.class, Map.of(
                "searchByUsername", args -> List.of(),
                "create", args -> createResponse,
                "get", args -> user,
                "delete", args -> {
                    deleted.add((String) args[0]);
                    return Response.noContent().build();
                }));
        return stub(RealmResource.class, Map.of("groups", args -> groups, "users", args -> users));
    }

    /**
     * A user recording {@code join <group id>} and {@code leave <group id>}; the call equal to
     * {@code failing} is recorded and then fails.
     */
    private static UserResource groupRecorder(List<String> calls, String failing) {
        Function<String, Function<Object[], Object>> record = verb -> args -> {
            String call = verb + " " + args[0];
            calls.add(call);
            if (call.equals(failing)) {
                throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
            }
            return null;
        };
        return stub(UserResource.class, Map.of("joinGroup", record.apply("join"), "leaveGroup", record.apply("leave")));
    }

    private static AccountService service(RealmResource realm) {
        AccountService service = new AccountService() {
            @Override
            RealmResource realm() {
                return realm;
            }
        };
        service.keycloakRealm = new KeycloakRealm("http://kc", "presserl");
        service.passPhrases = new PassPhraseGenerator();
        return service;
    }

    /**
     * An interface stub answering the named methods; every other call fails the test.
     */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, Function<Object[], Object>> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            Function<Object[], Object> answer = answers.get(method.getName());
            if (answer == null) {
                throw new UnsupportedOperationException("unexpected call " + type.getSimpleName() + "." + method.getName());
            }
            return answer.apply(args);
        });
    }
}
