package info.unterrainer.presserl.media;

import java.time.Instant;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An uploaded, re-encoded image; the bytes live in the object store under {@link #objectKey}. The
 * uploader is identified by the token subject; username and display name are snapshots, both
 * {@code null} once the uploader's account was deleted.
 */
@Entity
@Table(name = "media")
public class MediaEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * {@code 0} for a new upload; every edit increments it (reader URLs and ETags carry it).
     */
    @Column(nullable = false)
    public long version;

    @Column(name = "object_key", columnDefinition = "text", nullable = false, unique = true)
    public String objectKey;

    @Column(name = "content_type", columnDefinition = "text", nullable = false)
    public String contentType;

    @Column(nullable = false)
    public int width;

    @Column(nullable = false)
    public int height;

    @Column(name = "byte_size", nullable = false)
    public long byteSize;

    @Column(name = "uploader_sub", columnDefinition = "text", nullable = false)
    public String uploaderSub;

    @Column(name = "uploader_username", columnDefinition = "text")
    public String uploaderUsername;

    @Column(name = "uploader_display_name", columnDefinition = "text")
    public String uploaderDisplayName;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /**
     * Free text (subject, photographer, credit); {@code null} for none. See {@link MediaDetailsValidator}.
     */
    @Column(columnDefinition = "text")
    public String description;
}
