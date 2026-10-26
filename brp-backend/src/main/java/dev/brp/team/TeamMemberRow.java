package dev.brp.team;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "team_members",
       uniqueConstraints = @UniqueConstraint(name = "uq_team_member",
               columnNames = {"teamId", "playerUuid"}))
public class TeamMemberRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String teamId;
    private String playerUuid;
    /** OWNER | OFFICER | MEMBER */
    private String role;
    private Instant joinedAt = Instant.now();

    protected TeamMemberRow() {}

    public TeamMemberRow(String teamId, String playerUuid, String role) {
        this.teamId = teamId;
        this.playerUuid = playerUuid;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getTeamId() { return teamId; }
    public String getPlayerUuid() { return playerUuid; }
    public String getRole() { return role; }

    void setRole(String role) { this.role = role; }
}
