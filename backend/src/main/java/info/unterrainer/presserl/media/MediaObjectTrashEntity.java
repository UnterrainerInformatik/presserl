package info.unterrainer.presserl.media;

import java.time.Instant;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An object of a replaced media image that still has to be deleted from the object store
 * ({@link MediaTrashSweeper}); the row goes only once the object is gone.
 */
@Entity
@Table(name = "media_object_trash")
public class MediaObjectTrashEntity extends PanacheEntityBase {

    @Id
    @Column(name = "object_key", columnDefinition = "text")
    public String objectKey;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
