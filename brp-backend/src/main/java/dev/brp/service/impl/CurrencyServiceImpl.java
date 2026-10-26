package dev.brp.service.impl;

import dev.brp.common.ConflictException;
import dev.brp.common.annotation.Atomic;
import dev.brp.common.annotation.Idempotent;
import dev.brp.dto.CurrencyDtos.CurrencyResponse;
import dev.brp.dto.CurrencyDtos.RegisterCurrencyRequest;
import dev.brp.entity.CurrencyEntity;
import dev.brp.mapper.CurrencyMapper;
import dev.brp.repository.CurrencyRepository;
import dev.brp.service.CurrencyService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Owner-configured currency registry. The ledger refuses anything not on it
 * (docs/03 section 8): finance cannot invent money, that's the faucet's job.
 * Legacy-plugin compatibility lands as exchange-mirror accounts per adapter,
 * not by widening the registry.
 */
@Service
public class CurrencyServiceImpl implements CurrencyService {

    private final CurrencyRepository currencies;
    private final CurrencyMapper mapper;

    public CurrencyServiceImpl(CurrencyRepository currencies, CurrencyMapper mapper) {
        this.currencies = currencies;
        this.mapper = mapper;
    }

    @Override
    @Idempotent(key = "#req.code()")
    @Atomic
    public CurrencyResponse register(RegisterCurrencyRequest req) {
        currencies.findById(req.code()).ifPresent(c -> {
            throw ConflictException.of("economy.currency.duplicate");
        });
        return mapper.toDto(currencies.save(
                new CurrencyEntity(req.code(), req.name(), req.symbol(), req.decimals())));
    }

    @Override
    public List<CurrencyResponse> list() {
        return mapper.toDtoList(currencies.findAll());
    }

    @Override
    public boolean isTradable(String code) {
        return currencies.findById(code)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .isPresent();
    }
}
