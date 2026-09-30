package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.SectionRoleDto;

/**
 * A validated {@code POST /api/accounts} body: names trimmed, {@code lastName} empty when absent,
 * roles distinct in declaration order of {@link NewspaperRole}, section roles with distinct
 * sections in request order (whether the sections exist is checked on creation),
 * {@code sectionlessReporter} {@code false} when absent.
 */
public record CreateAccountRequest(String firstName, String lastName, String username, List<NewspaperRole> roles,
        List<SectionRoleDto> sectionRoles, boolean sectionlessReporter) {
}
