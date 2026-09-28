package info.unterrainer.presserl.issue;

import java.time.LocalDate;

/**
 * A validated issue body: the publication date, {@code null} for none.
 */
public record IssueDateInput(LocalDate publicationDate) {
}
