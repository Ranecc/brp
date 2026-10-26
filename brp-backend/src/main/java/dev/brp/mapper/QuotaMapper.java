package dev.brp.mapper;

import dev.brp.dto.QuotaDtos.ChunkIncidentResponse;
import dev.brp.dto.QuotaDtos.QuotaBucketResponse;
import dev.brp.entity.ChunkIncidentEntity;
import dev.brp.entity.QuotaBucketEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuotaMapper {
    QuotaBucketResponse toDto(QuotaBucketEntity entity);
    List<QuotaBucketResponse> toDtoList(List<QuotaBucketEntity> entities);
    ChunkIncidentResponse toDto(ChunkIncidentEntity entity);
    List<ChunkIncidentResponse> toIncidentDtoList(List<ChunkIncidentEntity> entities);
}
