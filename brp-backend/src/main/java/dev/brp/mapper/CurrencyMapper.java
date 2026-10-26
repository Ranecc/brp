package dev.brp.mapper;

import dev.brp.dto.CurrencyDtos.CurrencyResponse;
import dev.brp.entity.CurrencyEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CurrencyMapper {
    CurrencyResponse toDto(CurrencyEntity entity);
    List<CurrencyResponse> toDtoList(List<CurrencyEntity> entities);
}
