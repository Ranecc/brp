package dev.brp.player;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Player identity as reported by the agent's PlayerDirectory (ACL). UUID is
 * stored verbatim: dashed v4 for online-mode, un-dashed type-3 offline UUIDs
 * for LAN/authless servers — the agent decides, the backend doesn't judge.
 */
@Entity
@Table(name = "players")
public class PlayerEntity {

    @Id
    private String uuid;

    private String name;
    private Instant firstSeen = Instant.now();
    private Instant lastSeen = Instant.now();

    protected PlayerEntity() {}

    public PlayerEntity(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public Instant getFirstSeen() { return firstSeen; }
    public Instant getLastSeen() { return lastSeen; }

    void renameTo(String newName) {
        this.name = newName;
        this.lastSeen = Instant.now();
    }

    void touch() {
        this.lastSeen = Instant.now();
    }
}
