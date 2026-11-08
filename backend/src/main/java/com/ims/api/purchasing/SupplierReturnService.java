package com.ims.api.purchasing;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import com.ims.api.inventory.InventoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class SupplierReturnService {
    private final PurchaseOrderRepository orders;
    private final SupplierReturnRepository returns;
    private final SupplierCreditNoteRepository creditNotes;
    private final SupplierReturnIdempotencyRepository idempotencyKeys;
    private final AppUserRepository users;
    private final InventoryService inventory;
    private final Clock clock;

    public SupplierReturnService(PurchaseOrderRepository orders, SupplierReturnRepository returns,
                                 SupplierCreditNoteRepository creditNotes,
                                 SupplierReturnIdempotencyRepository idempotencyKeys,
                                 AppUserRepository users, InventoryService inventory, Clock clock) {
        this.orders = orders;
        this.returns = returns;
        this.creditNotes = creditNotes;
        this.idempotencyKeys = idempotencyKeys;
        this.users = users;
        this.inventory = inventory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierReturnDtos.Response> list(int page, int size) {
        return PageResponse.from(returns.findRecent(PageRequest.of(page, size))
                .map(SupplierReturnDtos.Response::from));
    }

    @Transactional(readOnly = true)
    public SupplierReturnDtos.Response get(long id) {
        return SupplierReturnDtos.Response.from(loadDetailed(id));
    }

    @Transactional(readOnly = true)
    public List<SupplierReturnDtos.ReturnableItemResponse> returnableItems(long purchaseOrderId) {
        PurchaseOrder order = orders.findDetailedById(purchaseOrderId)
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        Map<Long, BigDecimal> returned = returnedQuantities(purchaseOrderId);
        return order.getItems().stream()
                .filter(item -> item.getReceivedQuantity().compareTo(returned.getOrDefault(item.getId(), BigDecimal.ZERO)) > 0)
                .map(item -> SupplierReturnDtos.ReturnableItemResponse.from(item,
                        returned.getOrDefault(item.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public SupplierReturnDtos.Response create(SupplierReturnDtos.CreateRequest request, String actorEmail) {
        String key = request.idempotencyKey().toString();
        String requestHash = createRequestHash(request);
        if (idempotencyKeys.reserveIfAbsent(key, requestHash) == 0) {
            SupplierReturnIdempotencyKey existingKey = idempotencyKeys.findForUpdate(key)
                    .orElseThrow(() -> new IllegalStateException("Supplier return idempotency reservation disappeared"));
            if (!existingKey.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict("Supplier return idempotency key was already used for a different request");
            }
            SupplierReturn existing = existingKey.getSupplierReturn();
            if (existing == null) throw new IllegalStateException("Supplier return idempotency reservation has no return");
            return SupplierReturnDtos.Response.from(loadDetailed(existing.getId()));
        }

        PurchaseOrder order = orders.findDetailedForUpdate(request.purchaseOrderId())
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        Map<Long, PurchaseOrderItem> orderItems = order.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));
        Map<Long, BigDecimal> returned = returnedQuantities(request.purchaseOrderId());
        Set<Long> seenLines = new HashSet<>();
        ArrayList<SupplierReturnDtos.LineRequest> validated = new ArrayList<>();
        for (SupplierReturnDtos.LineRequest line : request.items()) {
            if (!seenLines.add(line.purchaseOrderItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A purchase order line may appear only once per supplier return");
            }
            PurchaseOrderItem orderItem = orderItems.get(line.purchaseOrderItemId());
            if (orderItem == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Return line does not belong to the selected purchase order");
            }
            BigDecimal returnable = orderItem.getReceivedQuantity()
                    .subtract(returned.getOrDefault(orderItem.getId(), BigDecimal.ZERO));
            if (line.quantity().signum() <= 0 || line.quantity().stripTrailingZeros().scale() > 3
                    || line.damagedQuantity().signum() < 0
                    || line.damagedQuantity().stripTrailingZeros().scale() > 3
                    || line.damagedQuantity().compareTo(line.quantity()) > 0
                    || line.quantity().compareTo(returnable) > 0) {
                throw ApiException.conflict("Return quantity must be positive, within received quantity, and include valid damaged quantity");
            }
            validated.add(line);
        }

        AppUser actor = requireUser(actorEmail);
        SupplierReturn supplierReturn = new SupplierReturn(nextReturnNumber(), order, request.reason().trim(),
                clean(request.notes()), key, requestHash, actor, clock.instant());
        short lineNumber = 1;
        for (SupplierReturnDtos.LineRequest line : validated) {
            supplierReturn.addItem(new SupplierReturnItem(lineNumber++, orderItems.get(line.purchaseOrderItemId()),
                    line.quantity(), line.damagedQuantity()));
        }
        SupplierReturn saved = returns.saveAndFlush(supplierReturn);
        SupplierReturnIdempotencyKey reservedKey = idempotencyKeys.findForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("Supplier return idempotency reservation disappeared"));
        reservedKey.setSupplierReturn(saved);
        idempotencyKeys.saveAndFlush(reservedKey);
        return SupplierReturnDtos.Response.from(loadDetailed(saved.getId()));
    }

    @Transactional
    public SupplierReturnDtos.Response approve(long id, String reviewerEmail) {
        SupplierReturn supplierReturn = loadForUpdate(id);
        transition(supplierReturn, value -> value.approve(requireUser(reviewerEmail), clock.instant()));
        return SupplierReturnDtos.Response.from(loadDetailed(id));
    }

    @Transactional
    public SupplierReturnDtos.Response reject(long id, String reviewerEmail, SupplierReturnDtos.RejectRequest request) {
        SupplierReturn supplierReturn = loadForUpdate(id);
        transition(supplierReturn, value -> value.reject(requireUser(reviewerEmail), clock.instant(), request.reason().trim()));
        return SupplierReturnDtos.Response.from(loadDetailed(id));
    }

    @Transactional
    public SupplierReturnDtos.Response dispatch(long id, String dispatcherEmail) {
        SupplierReturn supplierReturn = loadForUpdate(id);
        AppUser dispatcher = requireUser(dispatcherEmail);
        if (supplierReturn.getStatus() != SupplierReturnStatus.APPROVED) {
            throw ApiException.conflict("Only an approved supplier return can be dispatched");
        }
        PurchaseOrder order = supplierReturn.getPurchaseOrder();
        for (SupplierReturnItem item : supplierReturn.getItems()) {
            String movementKey = UUID.nameUUIDFromBytes(("ims:supplier-return:" + id + ":" + item.getId())
                    .getBytes(StandardCharsets.UTF_8)).toString();
            inventory.dispatchSupplierReturnStock(item.getPurchaseOrderItem().getProduct().getId(),
                    order.getWarehouse().getId(), item.getQuantity(), item.getDamagedQuantity(), dispatcherEmail,
                    movementKey, supplierReturn.getReturnNumber(), item.getId());
        }
        transition(supplierReturn, value -> value.dispatch(dispatcher, clock.instant()));
        return SupplierReturnDtos.Response.from(loadDetailed(id));
    }

    @Transactional
    public SupplierReturnDtos.Response cancel(long id, String actorEmail) {
        SupplierReturn supplierReturn = loadForUpdate(id);
        transition(supplierReturn, value -> value.cancel(requireUser(actorEmail), clock.instant()));
        return SupplierReturnDtos.Response.from(loadDetailed(id));
    }

    @Transactional
    public SupplierReturnDtos.Response recordCreditNote(long id, SupplierReturnDtos.CreditNoteRequest request,
                                                         String actorEmail) {
        SupplierReturn supplierReturn = loadForUpdate(id);
        String key = request.idempotencyKey().toString();
        String requestHash = creditRequestHash(id, request);
        SupplierCreditNote existing = creditNotes.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict("Supplier credit idempotency key was already used for a different request");
            }
            return SupplierReturnDtos.Response.from(loadDetailed(existing.getSupplierReturn().getId()));
        }
        if (supplierReturn.getStatus() != SupplierReturnStatus.DISPATCHED) {
            throw ApiException.conflict("A supplier credit note can be recorded only after dispatch");
        }
        AppUser actor = requireUser(actorEmail);
        SupplierCreditNote note = new SupplierCreditNote(request.supplierReference().trim(), request.amount(),
                supplierReturn.getCurrencyCode(), clean(request.notes()), actor, key, requestHash, clock.instant());
        supplierReturn.addCreditNote(note);
        returns.saveAndFlush(supplierReturn);
        return SupplierReturnDtos.Response.from(loadDetailed(id));
    }

    private SupplierReturn loadDetailed(long id) {
        return returns.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("Supplier return not found"));
    }

    private SupplierReturn loadForUpdate(long id) {
        return returns.findForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Supplier return not found"));
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
    }

    private void transition(SupplierReturn supplierReturn, Consumer<SupplierReturn> action) {
        try {
            action.accept(supplierReturn);
        } catch (IllegalStateException exception) {
            throw ApiException.conflict(exception.getMessage());
        }
    }

    private Map<Long, BigDecimal> returnedQuantities(long purchaseOrderId) {
        return returns.findAllByPurchaseOrderId(purchaseOrderId).stream()
                .filter(value -> value.getStatus() == SupplierReturnStatus.PENDING_APPROVAL
                        || value.getStatus() == SupplierReturnStatus.APPROVED
                        || value.getStatus() == SupplierReturnStatus.DISPATCHED)
                .flatMap(value -> value.getItems().stream())
                .collect(Collectors.groupingBy(item -> item.getPurchaseOrderItem().getId(),
                        Collectors.reducing(BigDecimal.ZERO, SupplierReturnItem::getQuantity, BigDecimal::add)));
    }

    private String nextReturnNumber() {
        return "SR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String createRequestHash(SupplierReturnDtos.CreateRequest request) {
        StringBuilder canonical = new StringBuilder();
        addHashField(canonical, Long.toString(request.purchaseOrderId()));
        addHashField(canonical, request.reason().trim());
        addHashField(canonical, clean(request.notes()));
        request.items().stream().sorted(Comparator.comparing(SupplierReturnDtos.LineRequest::purchaseOrderItemId))
                .forEach(item -> {
                    addHashField(canonical, Long.toString(item.purchaseOrderItemId()));
                    addHashField(canonical, item.quantity().stripTrailingZeros().toPlainString());
                    addHashField(canonical, item.damagedQuantity().stripTrailingZeros().toPlainString());
                });
        return sha256(canonical.toString());
    }

    private String creditRequestHash(long id, SupplierReturnDtos.CreditNoteRequest request) {
        StringBuilder canonical = new StringBuilder();
        addHashField(canonical, Long.toString(id));
        addHashField(canonical, request.supplierReference().trim());
        addHashField(canonical, request.amount().stripTrailingZeros().toPlainString());
        addHashField(canonical, clean(request.notes()));
        return sha256(canonical.toString());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void addHashField(StringBuilder canonical, String value) {
        if (value == null) canonical.append("-1:");
        else canonical.append(value.length()).append(':').append(value);
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
