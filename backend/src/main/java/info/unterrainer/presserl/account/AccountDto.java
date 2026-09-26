package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * One account as listed by {@code GET /api/accounts}; {@code lastName} is empty when unset.
 */
public record AccountDto(String id, String username, String firstName, String lastName, List<NewspaperRole> roles,
        boolean enabled) {
}
