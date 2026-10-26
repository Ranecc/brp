package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A tenant team. source = NATIVE (BRP authority: create/invite/leave via API)
 * or a compat source like FTBTEAMS (game-side authority: agent pulls rosters,
 * backend mirrors read-only — native management endpoints refuse these rows).
 */
@Entity
@Table(name = "teams")
public class TeamEntity {

    @Id
    private String id;

    private String name;
    private String tag;
    private String source = "NATIVE";
    private Instant createdAt = Instant.now();

    protected TeamEntity() {}

    public TeamEntity(String id, String name, String tag, String source) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.source = source;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTag() { return tag; }
    public String getSource() { return source; }

    public void updateFrom(String name, String tag) {
        this.name = name;
        this.tag = tag;
    }
}
