package dev.brp.player;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only rename history (audit + anomaly features feed on this). */
@Entity
@Table(name = "player_name_history")
public class PlayerNameHistoryRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uuid;
    private String oldName;
    private String newName;
    private Instant changedAt = Instant.now();

    protected PlayerNameHistoryRow() {}

    public PlayerNameHistoryRow(String uuid, String oldName, String newName) {
        this.uuid = uuid;
        this.oldName = oldName;
        this.newName = newName;
    }

    public String getUuid() { return uuid; }
    public String getOldName() { return oldName; }
    public String getNewName() { return newName; }
}
