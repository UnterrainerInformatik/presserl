package info.unterrainer.presserl.section;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reads and writes {@link SectionRoleEntity} rows. Lists are ordered by section position (ties by
 * section id), like the sections themselves.
 */
@ApplicationScoped
public class SectionRoleStore {

    private static final String BY_POSITION = " order by s.position, s.id";

    /**
     * The account's role per section id.
     */
    @WithSession
    public Uni<Map<Long, SectionRole>> rolesOf(String accountId) {
        return SectionRoleEntity.<SectionRoleEntity>list("accountId", accountId).map(rows -> {
            Map<Long, SectionRole> roles = new HashMap<>();
            rows.forEach(row -> roles.put(row.sectionId, row.role));
            return roles;
        });
    }

    /**
     * The account's section roles with section names, by position.
     */
    @WithSession
    public Uni<List<NamedSectionRoleDto>> namedRolesOf(String accountId) {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select r.sectionId, s.name, r.role from SectionRoleEntity r, SectionEntity s "
                        + "where s.id = r.sectionId and r.accountId = :account" + BY_POSITION,
                Object[].class)
                .setParameter("account", accountId)
                .getResultList())
                .map(rows -> rows.stream()
                        .map(row -> new NamedSectionRoleDto((Long) row[0], (String) row[1], (SectionRole) row[2]))
                        .toList());
    }

    /**
     * Every account's section roles by account id, each by position.
     */
    @WithSession
    public Uni<Map<String, List<SectionRoleDto>>> byAccount() {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select r.accountId, r.sectionId, r.role from SectionRoleEntity r, SectionEntity s "
                        + "where s.id = r.sectionId" + BY_POSITION,
                Object[].class)
                .getResultList())
                .map(rows -> {
                    Map<String, List<SectionRoleDto>> roles = new HashMap<>();
                    rows.forEach(row -> roles.computeIfAbsent((String) row[0], id -> new ArrayList<>())
                            .add(new SectionRoleDto((Long) row[1], (SectionRole) row[2])));
                    return roles;
                });
    }

    /**
     * The ids of all sections by position.
     */
    @WithSession
    public Uni<List<Long>> sectionIds() {
        return Panache.getSession().flatMap(session -> session.createSelectionQuery(
                "select s.id from SectionEntity s" + BY_POSITION, Long.class).getResultList());
    }

    /**
     * The account ids of the section editors per section id; sections without one are missing.
     */
    @WithSession
    public Uni<Map<Long, Set<String>>> sectionEditors() {
        return SectionRoleEntity.<SectionRoleEntity>list("role", SectionRole.SECTION_EDITOR).map(rows -> {
            Map<Long, Set<String>> editors = new HashMap<>();
            rows.forEach(row -> editors.computeIfAbsent(row.sectionId, id -> new HashSet<>()).add(row.accountId));
            return editors;
        });
    }

    /**
     * The members of a section with their roles, keyed by account id.
     */
    @WithSession
    public Uni<Map<String, SectionRole>> members(long sectionId) {
        return SectionRoleEntity.<SectionRoleEntity>list("sectionId", sectionId).map(rows -> {
            Map<String, SectionRole> members = new LinkedHashMap<>();
            rows.forEach(row -> members.put(row.accountId, row.role));
            return members;
        });
    }

    /**
     * The account's role in the section, {@code null} when it has none.
     */
    @WithSession
    public Uni<SectionRole> roleOf(long sectionId, String accountId) {
        return SectionRoleEntity.<SectionRoleEntity>findById(new SectionRoleId(sectionId, accountId))
                .map(row -> row == null ? null : row.role);
    }

    /**
     * Stores the section roles of a new account in one transaction.
     */
    @WithTransaction
    public Uni<Void> insert(String accountId, List<SectionRoleDto> roles, String assignedBy) {
        Instant now = now();
        List<SectionRoleEntity> rows = roles.stream().map(role -> row(role.sectionId(), accountId, role.role(),
                assignedBy, now)).toList();
        return SectionRoleEntity.persist(rows);
    }

    /**
     * Makes {@code requested} the account's section roles in one transaction: rows of sections not
     * requested are deleted, rows with another role are updated ({@code assignedBy} and
     * {@code assignedAt} refreshed), missing rows are inserted; unchanged rows stay untouched.
     */
    @WithTransaction
    public Uni<Void> replace(String accountId, List<SectionRoleDto> requested, String assignedBy) {
        Map<Long, SectionRole> wanted = new HashMap<>();
        requested.forEach(role -> wanted.put(role.sectionId(), role.role()));
        return SectionRoleEntity.<SectionRoleEntity>list("accountId", accountId).flatMap(rows -> {
            Instant now = now();
            Uni<Void> deletions = Uni.createFrom().voidItem();
            for (SectionRoleEntity row : rows) {
                SectionRole next = wanted.remove(row.sectionId);
                if (next == null) {
                    deletions = deletions.flatMap(done -> row.delete());
                } else if (next != row.role) {
                    row.role = next;
                    row.assignedBy = assignedBy;
                    row.assignedAt = now;
                }
            }
            List<SectionRoleEntity> inserts = requested.stream().filter(role -> wanted.containsKey(role.sectionId()))
                    .map(role -> row(role.sectionId(), accountId, role.role(), assignedBy, now)).toList();
            return inserts.isEmpty() ? deletions : deletions.flatMap(done -> SectionRoleEntity.persist(inserts));
        });
    }

    /**
     * Gives the account {@code role} in the section, replacing its current one.
     */
    @WithTransaction
    public Uni<Void> put(long sectionId, String accountId, SectionRole role, String assignedBy) {
        return SectionRoleEntity.<SectionRoleEntity>findById(new SectionRoleId(sectionId, accountId))
                .flatMap(existing -> {
                    if (existing == null) {
                        return row(sectionId, accountId, role, assignedBy, now()).persist().replaceWithVoid();
                    }
                    existing.role = role;
                    existing.assignedBy = assignedBy;
                    existing.assignedAt = now();
                    return Uni.createFrom().voidItem();
                });
    }

    /**
     * Removes the account's role in the section; {@code false} when it had none.
     */
    @WithTransaction
    public Uni<Boolean> remove(long sectionId, String accountId) {
        return SectionRoleEntity.delete("sectionId = ?1 and accountId = ?2", sectionId, accountId)
                .map(count -> count > 0);
    }

    private static SectionRoleEntity row(long sectionId, String accountId, SectionRole role, String assignedBy,
            Instant now) {
        SectionRoleEntity row = new SectionRoleEntity();
        row.sectionId = sectionId;
        row.accountId = accountId;
        row.role = role;
        row.assignedBy = assignedBy;
        row.assignedAt = now;
        return row;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
