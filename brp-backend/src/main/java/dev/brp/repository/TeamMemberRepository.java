package dev.brp.repository;
import dev.brp.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMemberRow, Long> {
    List<TeamMemberRow> findByTeamId(String teamId);
    List<TeamMemberRow> findByPlayerUuid(String playerUuid);
    Optional<TeamMemberRow> findByTeamIdAndPlayerUuid(String teamId, String playerUuid);
    void deleteByTeamId(String teamId);
}
