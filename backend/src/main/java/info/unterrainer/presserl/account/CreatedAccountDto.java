package info.unterrainer.presserl.account;

/**
 * Response of {@code POST /api/accounts}: the new account and its generated password, which
 * exists only in this response.
 */
public record CreatedAccountDto(AccountDto account, String password) {

    @Override
    public String toString() {
        return "CreatedAccountDto[account=" + account + ", password=***]";
    }
}
