package info.unterrainer.presserl.newspaper;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The newspaper singleton row: configuration layer 3, overrides only.
 */
@Entity
@Table(name = "newspaper")
public class NewspaperEntity extends PanacheEntityBase {

    public static final long SINGLETON_ID = 1L;

    @Id
    public Long id;

    @Column(columnDefinition = "text")
    public String name;

    @Column(columnDefinition = "text")
    public String subtitle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> settings = new HashMap<>();
}
