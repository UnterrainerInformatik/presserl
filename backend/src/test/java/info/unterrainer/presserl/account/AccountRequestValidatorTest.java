package info.unterrainer.presserl.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;
import jakarta.ws.rs.core.Response.Status;

/**
 * {@link AccountRequestValidator#validateRoles}: the {@code PUT /api/accounts/{id}/roles} body.
 */
class AccountRequestValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void validBody() {
        EditRolesRequest request = AccountRequestValidator.validateRoles(json("""
                {"roles": ["READER", "EDITOR_IN_CHIEF", "READER"],
                 "sectionRoles": [{"sectionId": 3, "role": "REPORTER"}, {"sectionId": 1, "role": "SECTION_EDITOR"}]}"""));

        assertThat(request.roles()).containsExactly(NewspaperRole.EDITOR_IN_CHIEF, NewspaperRole.READER);
        assertThat(request.sectionRoles()).containsExactly(new SectionRoleDto(3, SectionRole.REPORTER),
                new SectionRoleDto(1, SectionRole.SECTION_EDITOR));
    }

    @Test
    void sectionRolesOnly() {
        EditRolesRequest request = AccountRequestValidator.validateRoles(json("""
                {"roles": [], "sectionRoles": [{"sectionId": 3, "role": "REPORTER"}]}"""));

        assertThat(request.roles()).isEmpty();
        assertThat(request.sectionRoles()).hasSize(1);
    }

    @Test
    void missingSectionRoles() {
        assertInvalid("{\"roles\": [\"READER\"]}", "sectionRoles");
        assertInvalid("{\"roles\": [\"READER\"], \"sectionRoles\": null}", "sectionRoles");
    }

    @Test
    void missingRoles() {
        assertInvalid("{\"sectionRoles\": [{\"sectionId\": 3, \"role\": \"REPORTER\"}]}", "roles");
    }

    @Test
    void bothMissing() {
        assertInvalid("{}", "roles", "sectionRoles");
    }

    @Test
    void emptyBoth() {
        assertInvalid("{\"roles\": [], \"sectionRoles\": []}", "roles");
    }

    @Test
    void duplicateSection() {
        assertInvalid("""
                {"roles": ["READER"],
                 "sectionRoles": [{"sectionId": 3, "role": "REPORTER"}, {"sectionId": 3, "role": "SECTION_EDITOR"}]}""",
                "sectionRoles");
    }

    @Test
    void unknownRole() {
        assertInvalid("{\"roles\": [\"EDITOR\"], \"sectionRoles\": []}", "roles");
        assertInvalid("{\"roles\": [], \"sectionRoles\": [{\"sectionId\": 3, \"role\": \"PUBLISHER\"}]}",
                "sectionRoles");
    }

    @Test
    void unknownField() {
        assertInvalid("{\"roles\": [\"READER\"], \"sectionRoles\": [], \"firstName\": \"Max\"}", "firstName");
    }

    @Test
    void notAnObject() {
        assertInvalid("[]", (String) null);
    }

    private static void assertInvalid(String body, String... fields) {
        assertThatThrownBy(() -> AccountRequestValidator.validateRoles(json(body)))
                .isInstanceOfSatisfying(AccountException.class, e -> {
                    assertThat(e.status()).isEqualTo(Status.BAD_REQUEST);
                    assertThat(e.errors()).extracting(FieldError::field).containsExactly(fields);
                });
    }

    private static JsonNode json(String body) {
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
