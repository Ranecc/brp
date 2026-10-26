package dev.brp.mapper;

import dev.brp.contract.LedgerDtos.AccountDto;
import dev.brp.entity.AccountEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountDto toDto(AccountEntity entity);
}
