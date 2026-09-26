package info.unterrainer.presserl.account;

import java.util.List;

import info.unterrainer.presserl.auth.NewspaperRole;

/**
 * A validated {@code POST /api/accounts} body: names trimmed, {@code lastName} empty when absent,
 * roles distinct in declaration order of {@link NewspaperRole}.
 */
public record CreateAccountRequest(String firstName, String lastName, String username, List<NewspaperRole> roles) {
}
