package info.unterrainer.presserl.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.unterrainer.presserl.api.FieldError;
import info.unterrainer.presserl.article.ApprovalLevel;
import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.SectionRole;
import info.unterrainer.presserl.section.SectionRoleDto;
import info.unterrainer.presserl.trust.TrustScope;
import jakarta.ws.rs.core.Response.Status;

/**
 * {@link AccountRequestValidator#validateRoles}: the {@code PUT /api/accounts/{id}/roles} body;
 * {@link AccountRequestValidator#validateTrust}: the {@code PUT /api/accounts/{id}/trust} body;
 * {@link AccountRequestValidator#isUsername}: the username rules on their own ({@code /qr}).
 */
class AccountRequestValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Long> SECTION_IDS = List.of(1L, 3L);

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

    @Test
    void validTrustBodies() {
        assertThat(AccountRequestValidator.validateTrust(json("""
                {"level": "SECTION_EDITOR", "sectionId": 3, "trusted": true}"""), SECTION_IDS))
                .isEqualTo(new SetTrustRequest(new TrustScope(ApprovalLevel.SECTION_EDITOR, 3L), true));
        assertThat(AccountRequestValidator.validateTrust(json("""
                {"level": "PUBLISHER", "sectionId": null, "trusted": false}"""), SECTION_IDS))
                .isEqualTo(new SetTrustRequest(new TrustScope(ApprovalLevel.PUBLISHER, null), false));
        assertThat(AccountRequestValidator.validateTrust(json("""
                {"level": "EDITOR_IN_CHIEF", "trusted": true}"""), SECTION_IDS).scope())
                .isEqualTo(new TrustScope(ApprovalLevel.EDITOR_IN_CHIEF, null));
    }

    @Test
    void trustSectionRequiredForSectionEditor() {
        assertInvalidTrust("{\"level\": \"SECTION_EDITOR\", \"trusted\": true}", "sectionId");
    }

    @Test
    void trustSectionOnlyForSectionEditor() {
        assertInvalidTrust("{\"level\": \"PUBLISHER\", \"sectionId\": 1, \"trusted\": true}", "sectionId");
    }

    @Test
    void trustSectionMustExist() {
        assertInvalidTrust("{\"level\": \"SECTION_EDITOR\", \"sectionId\": 2, \"trusted\": true}", "sectionId");
        assertInvalidTrust("{\"level\": \"SECTION_EDITOR\", \"sectionId\": \"1\", \"trusted\": true}", "sectionId");
    }

    @Test
    void everyTrustViolationIsReported() {
        assertInvalidTrust("{\"level\": \"REPORTER\", \"trusted\": \"yes\", \"note\": 1}", "note", "level", "trusted");
        assertInvalidTrust("{}", "level", "trusted");
        assertInvalidTrust("[]", (String) null);
    }

    private static void assertInvalidTrust(String body, String... fields) {
        assertThatThrownBy(() -> AccountRequestValidator.validateTrust(json(body), SECTION_IDS))
                .isInstanceOfSatisfying(AccountException.class, e -> {
                    assertThat(e.status()).isEqualTo(Status.BAD_REQUEST);
                    assertThat(e.errors()).extracting(FieldError::field).containsExactly(fields);
                });
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

    @ParameterizedTest
    @ValueSource(strings = { "lena", "lena-2", "anna-maria", "abc" })
    void usernames(String value) {
        assertThat(AccountRequestValidator.isUsername(value)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "Lena", "lena x", "lena--2", "-lena", "lena-", "ab", "lena&next=//evil", "lena#pw",
            "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz" })
    void notUsernames(String value) {
        assertThat(AccountRequestValidator.isUsername(value)).isFalse();
    }
}
