package info.unterrainer.presserl.article;

import info.unterrainer.presserl.issue.IssueEntity;
import info.unterrainer.presserl.section.SectionEntity;

/**
 * An article together with one of its revisions (the latest unless stated otherwise), its section
 * ({@code null} only for an article the default-section bootstrap has not filed yet) and its issue
 * ({@code null} for none).
 */
public record ArticleView(ArticleEntity article, ArticleRevisionEntity revision, SectionEntity section,
        IssueEntity issue) {
}
