package info.unterrainer.presserl.section;

/**
 * An account holding a role in a section; {@code lastName} is empty when unset.
 */
public record MemberDto(String accountId, String username, String firstName, String lastName, SectionRole role) {
}
