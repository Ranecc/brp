package dev.brp.mapper;

import dev.brp.entity.TeamEntity;
import dev.brp.entity.TeamMemberRow;
import dev.brp.dto.TeamResponse;
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
