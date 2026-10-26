package dev.brp.mapper;

import dev.brp.entity.PlayerEntity;
import dev.brp.dto.PlayerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlayerMapper {
    PlayerResponse toDto(PlayerEntity entity);
}
