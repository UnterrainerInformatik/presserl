package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;
import info.unterrainer.presserl.section.SectionRoleDto;

/**
 * A validated {@code PUT /api/accounts/{id}/roles} body: the complete roles the account shall hold,
 * roles distinct in declaration order of {@link NewspaperRole}, section roles with distinct sections
 * in request order (whether the sections exist is checked on the edit), {@code sectionlessReporter}
 * {@code null} when absent (the marker stays, except for the automatic one).
 */
public record EditRolesRequest(List<NewspaperRole> roles, List<SectionRoleDto> sectionRoles,
        Boolean sectionlessReporter) {
}
