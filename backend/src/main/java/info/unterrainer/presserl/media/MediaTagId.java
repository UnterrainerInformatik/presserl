package info.unterrainer.presserl.media;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key of {@link MediaTagEntity}.
 */
public class MediaTagId implements Serializable {

    public Long mediaId;
    public String nameKey;

    public MediaTagId() {
    }

    public MediaTagId(Long mediaId, String nameKey) {
        this.mediaId = mediaId;
        this.nameKey = nameKey;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MediaTagId other && Objects.equals(mediaId, other.mediaId)
                && Objects.equals(nameKey, other.nameKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mediaId, nameKey);
    }
}
