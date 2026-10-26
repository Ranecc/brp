package dev.brp.team;

import dev.brp.team.dto.TeamDtos.CreateTeamRequest;
import dev.brp.team.dto.TeamDtos.TeamSyncRequest;
import dev.brp.team.dto.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Thin transport layer for the team domain. */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateTeamRequest req) {
        return teamService.create(req);
    }

    @PostMapping("/sync")
    public Map<String, Object> sync(@Valid @RequestBody TeamSyncRequest req) {
        return teamService.sync(req);
    }

    @GetMapping
    public List<TeamResponse> list(@RequestParam(required = false) String source) {
        return teamService.list(source);
    }
}
