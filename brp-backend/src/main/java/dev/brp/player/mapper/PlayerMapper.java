package dev.brp.player.mapper;

import dev.brp.player.PlayerEntity;
import dev.brp.player.dto.PlayerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlayerMapper {
    PlayerResponse toDto(PlayerEntity entity);
}
