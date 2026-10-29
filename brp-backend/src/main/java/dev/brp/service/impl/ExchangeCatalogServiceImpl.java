package dev.brp.service.impl;

import dev.brp.common.BadRequestException;

import dev.brp.dto.ExchangeDtos.CatalogEntryRequest;
import dev.brp.dto.ExchangeDtos.CatalogEntryResponse;
import dev.brp.dto.ExchangeDtos.ListingRequest;
import dev.brp.entity.ExchangeItemEntity;
import dev.brp.repository.ExchangeItemRepository;
import dev.brp.service.ExchangeCatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Listing governance (docs/14 §5). The resolution rule is three lines on
 * purpose; everything else here is making sure nobody sneaks a fourth in.
 */
@Service
public class ExchangeCatalogServiceImpl implements ExchangeCatalogService {

    public static final String PRESET_FREE = "free";
    public static final String PRESET_CURATED = "curated";
    public static final int PUSH_BATCH_CAP = 500;

    private final ExchangeItemRepository items;
    private final boolean freeBourse;

    public ExchangeCatalogServiceImpl(ExchangeItemRepository items,
                                      @Value("${brp.exchange.preset:free}") String preset) {
        this.items = items;
        this.freeBourse = PRESET_FREE.equalsIgnoreCase(preset);
        if (!freeBourse && !PRESET_CURATED.equalsIgnoreCase(preset)) {
            throw new IllegalArgumentException("brp.exchange.preset must be free|curated");
        }
    }

    @Override
    public void pushCatalog(List<CatalogEntryRequest> push) {
        if (push != null && push.size() > PUSH_BATCH_CAP) {
            throw new BadRequestException("exchange.catalog.too.large");
        }
        if (push == null) {
            return;
        }
        for (CatalogEntryRequest entry : push) {
            if (entry.itemId() == null || entry.itemId().isBlank()
                    || !entry.itemId().contains(":")) {
                throw new BadRequestException("exchange.item.id.invalid");
            }
            ExchangeItemEntity item = items.findById(entry.itemId()).orElse(null);
            if (item == null) {
                item = new ExchangeItemEntity(entry.itemId(),
                        displayName(entry.itemId(), entry.displayName()), 0L);
            } else if (entry.displayName() != null && !entry.displayName().isBlank()) {
                item.rename(entry.displayName());
            }
            items.save(item);
        }
    }

    @Override
    public CatalogEntryResponse list(String itemId, ListingRequest request) {
        ExchangeItemEntity item = items.findById(itemId)
                .orElseThrow(() -> new BadRequestException("exchange.item.unknown"));
        String state = request.listedState();
        if (!ExchangeItemEntity.DEFAULT.equals(state)
                && !ExchangeItemEntity.LISTED.equals(state)
                && !ExchangeItemEntity.BLOCKED.equals(state)) {
            throw new BadRequestException("exchange.listing.state.invalid");
        }
        if (request.displayName() != null && !request.displayName().isBlank()) {
            item.rename(request.displayName());
        }
        if (request.pinned() != null) {
            item.setPinned(request.pinned());
        }
        if (request.refCents() != null) {
            if (request.refCents() <= 0) {
                throw new BadRequestException("exchange.listing.ref.invalid");
            }
            item.setRefCents(request.refCents());
        }
        if (ExchangeItemEntity.LISTED.equals(state) && item.getRefCents() <= 0) {
            // the state does price the world: no listing without a posted price
            throw new BadRequestException("exchange.listing.ref.required");
        }
        item.setListedState(state);
        item.touch();
        return toDto(items.save(item));
    }

    @Override
    public List<CatalogEntryResponse> catalog() {
        return items.findAll().stream().map(ExchangeCatalogServiceImpl::toDto)
                .sorted(java.util.Comparator.comparing(CatalogEntryResponse::itemId))
                .toList();
    }

    @Override
    public boolean tradable(String itemId) {
        return items.findById(itemId)
                .map(item -> switch (item.getListedState()) {
                    case ExchangeItemEntity.BLOCKED -> false;
                    case ExchangeItemEntity.LISTED -> true;
                    default -> freeBourse;
                })
                .orElse(false);
    }

    @Override
    public CatalogEntryResponse entry(String itemId) {
        return items.findById(itemId).map(ExchangeCatalogServiceImpl::toDto)
                .orElseThrow(() -> new BadRequestException("exchange.item.unknown"));
    }

    /** "certus_quartz_crystal" → "Certus Quartz Crystal", unless named on push. */
    public static String displayName(String itemId, String provided) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        String path = itemId.substring(itemId.indexOf(':') + 1);
        String[] words = path.split("[_\\s]+");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(word.substring(1));
        }
        return name.length() > 0 ? name.toString() : path;
    }

    static CatalogEntryResponse toDto(ExchangeItemEntity item) {
        return new CatalogEntryResponse(item.getItemId(), item.getDisplayName(),
                item.getListedState(), item.getRefCents(), item.isPinned());
    }
}
