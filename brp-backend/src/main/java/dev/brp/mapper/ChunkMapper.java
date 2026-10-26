package dev.brp.mapper;

import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.entity.ManagedChunkEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChunkMapper {
    ChunkSlotResponse toDto(ManagedChunkEntity entity);
    List<ChunkSlotResponse> toDtoList(List<ManagedChunkEntity> entities);
}
