package info.unterrainer.presserl.article;

/**
 * The name of an article's author, a revision's author, a reviewer or an uploader as snapshot at
 * writing time; both {@code null} once that account was deleted ("former newsroom member").
 */
public record AuthorDto(String username, String displayName) {

    static AuthorDto of(ArticleEntity article) {
        return new AuthorDto(article.authorUsername, article.authorDisplayName);
    }

    static AuthorDto of(ArticleRevisionEntity revision) {
        return new AuthorDto(revision.authorUsername, revision.authorDisplayName);
    }
}
