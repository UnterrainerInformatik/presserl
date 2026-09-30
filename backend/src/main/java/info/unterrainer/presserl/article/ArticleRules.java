package info.unterrainer.presserl.article;

import info.unterrainer.presserl.newspaper.EffectiveSettings;

/**
 * The newspaper settings {@link ArticlePolicy} follows, so the policy stays a pure function.
 *
 * @param corrections whether higher levels may correct the articles of those below them
 *                    ({@code article.corrections})
 */
public record ArticleRules(boolean corrections) {

    public static ArticleRules of(EffectiveSettings settings) {
        return new ArticleRules(settings.articleCorrections());
    }
}
