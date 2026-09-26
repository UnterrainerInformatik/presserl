package info.unterrainer.presserl.admin

/** The `ArticleDto` example of the REST contract (design D5 / ai/primer/endpoints.md). */
const val ARTICLE = """
{
  "id": 42,
  "status": "PUBLISHED",
  "author": { "username": "papa", "displayName": "Papa" },
  "revision": 2,
  "liveRevision": 1,
  "hasUnpublishedChanges": true,
  "version": 5,
  "createdAt": "2026-09-26T10:00:00Z",
  "updatedAt": "2026-09-26T10:05:00Z",
  "publishedAt": "2026-09-26T10:01:00Z",
  "kicker": "Garden",
  "headline": "The pumpkin is huge",
  "subheadline": "",
  "lead": "Our pumpkin weighs 12 kilos.",
  "body": {
    "version": 1,
    "blocks": [
      { "type": "paragraph", "content": [ { "text": "It started " }, { "text": "in May", "bold": true }, { "text": "." } ] },
      { "type": "subhead", "text": "Watering" },
      { "type": "quote", "content": [ { "text": "Every day!" } ] },
      { "type": "list", "items": [ [ { "text": "Water" } ], [ { "text": "Sun" } ] ] }
    ]
  },
  "allowedActions": ["EDIT", "PUBLISH", "TAKE_OFFLINE"]
}
"""
