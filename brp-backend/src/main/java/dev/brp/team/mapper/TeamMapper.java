package dev.brp.team.mapper;

import dev.brp.team.TeamEntity;
import dev.brp.team.TeamMemberRow;
import dev.brp.team.dto.TeamResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TeamMapper {

    TeamResponse.MemberResponse toMemberDto(TeamMemberRow row);

    /** Team + roster assembled by the service; composition stays in the mapper. */
    default TeamResponse toDto(TeamEntity team, List<TeamMemberRow> members) {
        return new TeamResponse(team.getId(), team.getName(), team.getTag(),
                team.getSource(),
                members.stream().map(this::toMemberDto).toList());
    }
}
