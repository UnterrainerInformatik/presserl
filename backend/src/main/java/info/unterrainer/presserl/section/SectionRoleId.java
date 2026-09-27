package info.unterrainer.presserl.section;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key of {@link SectionRoleEntity}.
 */
public class SectionRoleId implements Serializable {

    public Long sectionId;
    public String accountId;

    public SectionRoleId() {
    }

    public SectionRoleId(Long sectionId, String accountId) {
        this.sectionId = sectionId;
        this.accountId = accountId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SectionRoleId other && Objects.equals(sectionId, other.sectionId)
                && Objects.equals(accountId, other.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sectionId, accountId);
    }
}
