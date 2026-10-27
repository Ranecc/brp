package dev.brp.service;

import dev.brp.dto.CurrencyDtos.CurrencyResponse;
import dev.brp.dto.CurrencyDtos.RegisterCurrencyRequest;
import dev.brp.dto.CurrencyDtos.UpdateCurrencyRequest;

import java.util.List;

/** Currency registry contract (impl: {@link dev.brp.service.impl.CurrencyServiceImpl}). */
public interface CurrencyService {

    CurrencyResponse register(RegisterCurrencyRequest req);

    /** Display-level edit: name/symbol/bearerItemId only. code is immutable (docs/03 section 8). */
    CurrencyResponse update(String code, UpdateCurrencyRequest req);

    List<CurrencyResponse> list();

    /** Ledger-side validation hook: registered AND ACTIVE, or finance says no. */
    boolean isTradable(String code);
}
