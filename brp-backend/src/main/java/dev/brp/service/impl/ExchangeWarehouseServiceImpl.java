package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.dto.ExchangeDtos.ClaimResponse;
import dev.brp.dto.ExchangeDtos.WarehouseRowResponse;
import dev.brp.entity.ExchangeClaimEntity;
import dev.brp.entity.ExchangeMovementEntity;
import dev.brp.entity.WarehouseRowEntity;
import dev.brp.repository.ExchangeClaimRepository;
import dev.brp.repository.ExchangeMovementRepository;
import dev.brp.repository.WarehouseRepository;
import dev.brp.service.ExchangeCatalogService;
import dev.brp.service.ExchangeWarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Custody (docs/14 §8). Every mutation is one movement row and one warehouse
 * delta in the same transaction; replays short-circuit on the movement's
 * idem key, so a retried deposit never inflates a shelf.
 */
@Service
public class ExchangeWarehouseServiceImpl implements ExchangeWarehouseService {

    private final WarehouseRepository warehouse;
    private final ExchangeMovementRepository movements;
    private final ExchangeClaimRepository claims;
    private final ExchangeCatalogService catalog;
    private final TransactionTemplate tx;

    public ExchangeWarehouseServiceImpl(WarehouseRepository warehouse,
                                        ExchangeMovementRepository movements,
                                        ExchangeClaimRepository claims,
                                        ExchangeCatalogService catalog,
                                        TransactionTemplate tx) {
        this.warehouse = warehouse;
        this.movements = movements;
        this.claims = claims;
        this.catalog = catalog;
        this.tx = tx;
    }

    @Override
    public WarehouseRowResponse deposit(String tenantId, String itemId, long qty, String idemKey) {
        requirePositive(qty);
        String key = "wh-dep:" + tenantId + ":" + itemId + ":" + idemKey;
        return tx.execute(status -> {
            Optional<ExchangeMovementEntity> replay = movements.findByIdemKey(key);
            if (replay.isPresent()) {
                return toDto(row(tenantId, itemId));
            }
            requireKnownItem(itemId);
            move(tenantId, itemId, qty, 0, ExchangeMovementEntity.DEPOSIT, null, key);
            return toDto(row(tenantId, itemId));
        });
    }

    @Override
    public ClaimResponse withdraw(String tenantId, String itemId, long qty, String idemKey) {
        requirePositive(qty);
        String key = "wh-out:" + tenantId + ":" + itemId + ":" + idemKey;
        return tx.execute(status -> {
            Optional<ExchangeMovementEntity> replay = movements.findByIdemKey(key);
            if (replay.isPresent()) {
                ExchangeClaimEntity claim = claims.findById(Long.parseLong(replay.get().getRefId()))
                        .orElseThrow(() -> new BadRequestException("exchange.claim.unknown"));
                return toDto(claim);
            }
            requireKnownItem(itemId);
            WarehouseRowEntity shelf = row(tenantId, itemId);
            if (shelf.getQtyFree() < qty) {
                throw new BadRequestException("exchange.warehouse.insufficient");
            }
            ExchangeClaimEntity claim = claims.save(new ExchangeClaimEntity(tenantId, itemId, qty));
            move(tenantId, itemId, -qty, 0, ExchangeMovementEntity.WITHDRAW_OUT,
                    String.valueOf(claim.getId()), key);
            return toDto(claim);
        });
    }

    @Override
    public void confirmWithdraw(String tenantId, long claimId) {
        tx.executeWithoutResult(status -> {
            ExchangeClaimEntity claim = ownedClaim(tenantId, claimId);
            if (!ExchangeClaimEntity.PENDING.equals(claim.getStatus())) {
                throw new BadRequestException("exchange.claim.closed");
            }
            claim.close(ExchangeClaimEntity.CONFIRMED);
            claims.save(claim);
        });
    }

    @Override
    public void releaseWithdraw(String tenantId, long claimId) {
        tx.executeWithoutResult(status -> {
            ExchangeClaimEntity claim = ownedClaim(tenantId, claimId);
            if (!ExchangeClaimEntity.PENDING.equals(claim.getStatus())) {
                throw new BadRequestException("exchange.claim.closed");
            }
            claim.close(ExchangeClaimEntity.RELEASED);
            claims.save(claim);
            move(tenantId, claim.getItemId(), claim.getQty(), 0,
                    ExchangeMovementEntity.WITHDRAW_RETURN,
                    String.valueOf(claim.getId()),
                    "wh-back:" + claim.getId());
        });
    }

    @Override
    public List<WarehouseRowResponse> holdings(String tenantId) {
        return warehouse.findByTenantId(tenantId).stream()
                .map(ExchangeWarehouseServiceImpl::toDto).toList();
    }

    @Override
    public List<ClaimResponse> claims(String tenantId) {
        return claims.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ExchangeWarehouseServiceImpl::toDto).toList();
    }

    @Override
    public void hold(String tenantId, String itemId, long qty, String refId, String idemKey) {
        tx.executeWithoutResult(status -> {
            if (movements.findByIdemKey(idemKey).isPresent()) {
                return;
            }
            WarehouseRowEntity shelf = row(tenantId, itemId);
            if (shelf.getQtyFree() < qty) {
                throw new BadRequestException("exchange.warehouse.insufficient");
            }
            move(tenantId, itemId, -qty, qty, ExchangeMovementEntity.HOLD, refId, idemKey);
        });
    }

    @Override
    public void release(String tenantId, String itemId, long qty, String refId, String idemKey) {
        tx.executeWithoutResult(status -> {
            if (movements.findByIdemKey(idemKey).isPresent()) {
                return;
            }
            move(tenantId, itemId, qty, -qty, ExchangeMovementEntity.RELEASE, refId, idemKey);
        });
    }

    @Override
    public void deliver(String sellerTenantId, String buyerTenantId, String itemId,
                        long qty, String refId, String idemKey) {
        tx.executeWithoutResult(status -> {
            if (movements.findByIdemKey(idemKey).isPresent()) {
                return;
            }
            move(sellerTenantId, itemId, 0, -qty, ExchangeMovementEntity.FILL_OUT, refId,
                    idemKey + ":out");
            move(buyerTenantId, itemId, qty, 0, ExchangeMovementEntity.FILL_IN, refId,
                    idemKey + ":in");
        });
    }

    @Override
    public void ship(String sellerTenantId, String buyerTenantId, String itemId,
                     long qty, String refId, String idemKey) {
        tx.executeWithoutResult(status -> {
            if (movements.findByIdemKey(idemKey).isPresent()) {
                return;
            }
            move(sellerTenantId, itemId, -qty, 0, ExchangeMovementEntity.FILL_OUT, refId,
                    idemKey + ":out");
            move(buyerTenantId, itemId, qty, 0, ExchangeMovementEntity.FILL_IN, refId,
                    idemKey + ":in");
        });
    }

    // --- internals ---

    private void move(String tenantId, String itemId, long deltaFree, long deltaHeld,
                      String kind, String refId, String idemKey) {
        WarehouseRowEntity shelf = row(tenantId, itemId);
        shelf.grow(deltaFree, deltaHeld);
        warehouse.save(shelf);
        movements.save(new ExchangeMovementEntity(tenantId, itemId, deltaFree, deltaHeld,
                kind, refId, idemKey));
    }

    private WarehouseRowEntity row(String tenantId, String itemId) {
        WarehouseRowEntity locked = warehouse.findForUpdate(tenantId, itemId);
        return locked != null ? locked
                : warehouse.saveAndFlush(new WarehouseRowEntity(tenantId, itemId));
    }

    private ExchangeClaimEntity ownedClaim(String tenantId, long claimId) {
        ExchangeClaimEntity claim = claims.findById(claimId)
                .orElseThrow(() -> new BadRequestException("exchange.claim.unknown"));
        if (!claim.getTenantId().equals(tenantId)) {
            throw new BadRequestException("exchange.claim.unknown");
        }
        return claim;
    }

    private void requireKnownItem(String itemId) {
        catalog.entry(itemId);
    }

    private void requirePositive(long qty) {
        if (qty <= 0) {
            throw new BadRequestException("exchange.warehouse.qty.invalid");
        }
    }

    static WarehouseRowResponse toDto(WarehouseRowEntity row) {
        return new WarehouseRowResponse(row.getTenantId(), row.getItemId(),
                row.getQtyFree(), row.getQtyHeld());
    }

    static ClaimResponse toDto(ExchangeClaimEntity claim) {
        return new ClaimResponse(claim.getId(), claim.getTenantId(), claim.getItemId(),
                claim.getQty(), claim.getStatus());
    }
}
