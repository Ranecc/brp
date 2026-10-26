package dev.brp.mapper;

import dev.brp.entity.InventorySnapshotEntity;
import dev.brp.dto.StockLevelDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InventoryMapper {
    StockLevelDto toDto(InventorySnapshotEntity entity);
    List<StockLevelDto> toDtoList(List<InventorySnapshotEntity> entities);
}
