package dev.brp.ledger.mapper;

import dev.brp.contract.LedgerDtos.AccountDto;
import dev.brp.ledger.AccountEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountDto toDto(AccountEntity entity);
}
