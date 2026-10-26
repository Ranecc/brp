package dev.brp.team;

import dev.brp.team.dto.TeamDtos.CreateTeamRequest;
import dev.brp.team.dto.TeamDtos.TeamSyncRequest;
import dev.brp.team.dto.TeamResponse;

import java.util.List;
import java.util.Map;

/** Tenant team domain contract (impl: {@link TeamServiceImpl}). */
public interface TeamService {

    Map<String, Object> create(CreateTeamRequest req);

    Map<String, Object> sync(TeamSyncRequest req);

    List<TeamResponse> list(String source);
}
