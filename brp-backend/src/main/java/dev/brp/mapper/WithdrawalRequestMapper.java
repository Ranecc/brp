package dev.brp.mapper;

import dev.brp.dto.ApprovalDtos.WithdrawalRequestResponse;
import dev.brp.entity.WithdrawalRequestEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WithdrawalRequestMapper {
    WithdrawalRequestResponse toDto(WithdrawalRequestEntity entity);
    List<WithdrawalRequestResponse> toDtoList(List<WithdrawalRequestEntity> entities);
}
