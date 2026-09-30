package info.unterrainer.presserl.media;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One tag of a media: {@link #name} in the spelling as stored, {@link #nameKey} its comparison key
 * ({@link MediaDetailsValidator#key}). A tag exists exactly while a row carries it.
 */
@Entity
@Table(name = "media_tag")
@IdClass(MediaTagId.class)
public class MediaTagEntity extends PanacheEntityBase {

    @Id
    @Column(name = "media_id")
    public Long mediaId;

    @Id
    @Column(name = "name_key", columnDefinition = "text")
    public String nameKey;

    @Column(columnDefinition = "text", nullable = false)
    public String name;
}
