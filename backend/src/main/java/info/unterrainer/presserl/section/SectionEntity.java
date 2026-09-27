package info.unterrainer.presserl.section;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A section of the newspaper. The slug is derived from the name once and never changes, so reader
 * URLs stay stable; {@code settings} holds section overrides (not exposed yet).
 */
@Entity
@Table(name = "section")
public class SectionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(columnDefinition = "text", nullable = false)
    public String name;

    @Column(columnDefinition = "text", nullable = false, updatable = false)
    public String slug;

    @Convert(converter = SectionColor.Converter.class)
    @Column(columnDefinition = "text", nullable = false)
    public SectionColor color;

    @Column(nullable = false)
    public int position;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> settings = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
