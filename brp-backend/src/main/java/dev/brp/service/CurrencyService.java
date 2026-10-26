package dev.brp.service;

import dev.brp.dto.CurrencyDtos.CurrencyResponse;
import dev.brp.dto.CurrencyDtos.RegisterCurrencyRequest;

import java.util.List;

/** Currency registry contract (impl: {@link dev.brp.service.impl.CurrencyServiceImpl}). */
public interface CurrencyService {

    CurrencyResponse register(RegisterCurrencyRequest req);

    List<CurrencyResponse> list();

    /** Ledger-side validation hook: registered AND ACTIVE, or finance says no. */
    boolean isTradable(String code);
}
