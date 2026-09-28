package info.unterrainer.presserl.media;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key of {@link MediaRenditionEntity}.
 */
public class MediaRenditionId implements Serializable {

    public Long mediaId;
    public String kind;

    public MediaRenditionId() {
    }

    public MediaRenditionId(Long mediaId, String kind) {
        this.mediaId = mediaId;
        this.kind = kind;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MediaRenditionId other && Objects.equals(mediaId, other.mediaId)
                && Objects.equals(kind, other.kind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mediaId, kind);
    }
}
