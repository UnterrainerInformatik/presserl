package info.unterrainer.presserl.article;

/**
 * An article together with one of its revisions (the latest unless stated otherwise).
 */
public record ArticleView(ArticleEntity article, ArticleRevisionEntity revision) {
}
