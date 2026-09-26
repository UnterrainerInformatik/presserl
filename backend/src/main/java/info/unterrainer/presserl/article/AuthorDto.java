package info.unterrainer.presserl.article;

public record AuthorDto(String username, String displayName) {

    static AuthorDto of(ArticleEntity article) {
        return new AuthorDto(article.authorUsername, article.authorDisplayName);
    }
}
