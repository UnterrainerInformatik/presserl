package info.unterrainer.presserl.article;

import info.unterrainer.presserl.section.SectionEntity;

/**
 * An article together with one of its revisions (the latest unless stated otherwise) and its
 * section ({@code null} only for an article the default-section bootstrap has not filed yet).
 */
public record ArticleView(ArticleEntity article, ArticleRevisionEntity revision, SectionEntity section) {
}
