package dev.brp.controller;

import dev.brp.dto.CurrencyDtos.CurrencyResponse;
import dev.brp.dto.CurrencyDtos.RegisterCurrencyRequest;
import dev.brp.dto.CurrencyDtos.UpdateCurrencyRequest;
import dev.brp.service.CurrencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The registry of legal tender; owners mint currencies here, not elsewhere. */
@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @PostMapping
    public CurrencyResponse register(@Valid @RequestBody RegisterCurrencyRequest req) {
        return currencyService.register(req);
    }

    /** Display-level edit: name/symbol/bearerItemId. code has no edit path on purpose. */
    @PatchMapping("/{code}")
    public CurrencyResponse update(@PathVariable String code,
                                   @Valid @RequestBody UpdateCurrencyRequest req) {
        return currencyService.update(code, req);
    }

    @GetMapping
    public List<CurrencyResponse> list() {
        return currencyService.list();
    }
}
