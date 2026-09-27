package info.unterrainer.presserl.account;

import info.unterrainer.presserl.trust.TrustScope;

/**
 * A validated {@code PUT /api/accounts/{id}/trust} body: the entry and whether it shall exist.
 */
public record SetTrustRequest(TrustScope scope, boolean trusted) {
}
