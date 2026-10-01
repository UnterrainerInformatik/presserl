package info.unterrainer.presserl.account;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.hibernate.reactive.mutiny.Mutiny;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Removes a deleted account's personal data from the database and keeps its content: withdraws the
 * pending submissions of the articles it authored (status and live revision stay, as on a withdrawal;
 * no review entry), sets every username and display-name snapshot of it to {@code NULL} (articles,
 * revisions, reviews, media; the {@code *_sub} columns stay), and deletes its section roles,
 * sectionless-reporter marker, the trust entries placed on it and its deletion request. Rows it set
 * for others ({@code assigned_by}, {@code set_by}) stay. Idempotent, and works for a sub whose
 * Keycloak user is already gone; {@code deploy/INSTALL.md} holds the same statements as SQL.
 */
@ApplicationScoped
public class AccountDataEraser {

    private static final List<String> ERASE = List.of(
            "update article set author_username = null, author_display_name = null where author_sub = ?1",
            "update article_revision set author_username = null, author_display_name = null where author_sub = ?1",
            "update article_review set reviewer_username = null, reviewer_display_name = null where reviewer_sub = ?1",
            "update media set uploader_username = null, uploader_display_name = null where uploader_sub = ?1",
            "delete from section_role where account_id = ?1",
            "delete from sectionless_reporter where account_id = ?1",
            "delete from trust where account_id = ?1",
            "delete from account_deletion_request where account_id = ?1");

    /**
     * Joins the caller's transaction if there is one.
     */
    @WithTransaction
    public Uni<Void> erase(String sub) {
        return Panache.getSession().flatMap(session -> withdrawSubmissions(session, sub)
                .flatMap(withdrawn -> Multi.createFrom().iterable(ERASE)
                        .onItem().transformToUniAndConcatenate(sql -> session.createNativeQuery(sql)
                                .setParameter(1, sub)
                                .executeUpdate())
                        .collect().last()))
                .replaceWithVoid();
    }

    /**
     * Ends every pending submission of the account's articles: a {@code SUBMITTED} article returns to
     * {@code DRAFT}, a published or offline one keeps its status; the version is incremented like on
     * a withdrawal through the entity.
     */
    private static Uni<Integer> withdrawSubmissions(Mutiny.Session session, String sub) {
        return session.createNativeQuery("update article set pending_level = null, "
                + "status = case when status = 'SUBMITTED' then 'DRAFT' else status end, "
                + "updated_at = ?2, version = version + 1 "
                + "where author_sub = ?1 and pending_level is not null")
                .setParameter(1, sub)
                .setParameter(2, Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .executeUpdate();
    }
}
