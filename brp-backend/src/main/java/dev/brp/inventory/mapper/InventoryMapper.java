package dev.brp.inventory.mapper;

import dev.brp.inventory.InventorySnapshotEntity;
import dev.brp.inventory.dto.StockLevelDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InventoryMapper {
    StockLevelDto toDto(InventorySnapshotEntity entity);
    List<StockLevelDto> toDtoList(List<InventorySnapshotEntity> entities);
}
