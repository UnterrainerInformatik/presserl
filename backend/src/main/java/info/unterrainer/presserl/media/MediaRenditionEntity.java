package info.unterrainer.presserl.media;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One rendition of a media ({@link RenditionKind}); the bytes live in the object store under
 * {@link #objectKey}.
 */
@Entity
@Table(name = "media_rendition")
@IdClass(MediaRenditionId.class)
public class MediaRenditionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "media_id")
    public Long mediaId;

    /**
     * {@link RenditionKind#value()}.
     */
    @Id
    @Column(columnDefinition = "text")
    public String kind;

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
}
