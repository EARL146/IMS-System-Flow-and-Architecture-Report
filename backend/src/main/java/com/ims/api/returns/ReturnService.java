package com.ims.api.returns;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.TrackingType;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import com.ims.api.customer.CustomerOrder;
import com.ims.api.customer.CustomerOrderItem;
import com.ims.api.customer.CustomerOrderRepository;
import com.ims.api.customer.CustomerOrderStatus;
import com.ims.api.customer.CustomerOrderStatusHistory;
import com.ims.api.customer.CustomerOrderStatusHistoryRepository;
import com.ims.api.customer.PaymentStatus;
import com.ims.api.inventory.InventoryService;
import com.ims.api.pos.PosSale;
import com.ims.api.pos.PosSaleItem;
import com.ims.api.pos.PosSaleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReturnService {
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(4);

    private final ReturnCaseRepository returnCases;
    private final ReturnCaseItemRepository returnItems;
    private final ReturnRefundRepository refunds;
    private final ReturnRequestIdempotencyRepository requestKeys;
    private final ReturnRefundIdempotencyRepository refundKeys;
    private final CustomerOrderRepository orders;
    private final CustomerOrderStatusHistoryRepository orderHistory;
    private final PosSaleRepository posSales;
    private final AppUserRepository users;
    private final InventoryService inventory;
    private final Clock clock;

    public ReturnService(
            ReturnCaseRepository returnCases,
            ReturnCaseItemRepository returnItems,
            ReturnRefundRepository refunds,
            ReturnRequestIdempotencyRepository requestKeys,
            ReturnRefundIdempotencyRepository refundKeys,
            CustomerOrderRepository orders,
            CustomerOrderStatusHistoryRepository orderHistory,
            PosSaleRepository posSales,
            AppUserRepository users,
            InventoryService inventory,
            Clock clock
    ) {
        this.returnCases = returnCases;
        this.returnItems = returnItems;
        this.refunds = refunds;
        this.requestKeys = requestKeys;
        this.refundKeys = refundKeys;
        this.orders = orders;
        this.orderHistory = orderHistory;
        this.posSales = posSales;
        this.users = users;
        this.inventory = inventory;
        this.clock = clock;
    }

    @Transactional
    public ReturnDtos.ReturnResponse requestCustomerOrderReturn(
            long orderId,
            String customerEmail,
            ReturnDtos.CreateReturnRequest request
    ) {
        AppUser customer = users.findByEmailIgnoreCase(customerEmail).orElseThrow(ApiException::unauthorized);
        String key = request.idempotencyKey().toString();
        String hash = requestHash(ReturnSourceType.CUSTOMER_ORDER, orderId, customer.getId(), request);
        ReturnCase replay = reserveRequest(key, hash);
        if (replay != null) return ReturnDtos.ReturnResponse.from(loadDetailed(replay.getId()));

        CustomerOrder order = orders.findDetailedForUpdateByIdAndCustomer(orderId, customer.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        CustomerOrderStatus previous = order.getStatus();
        try {
            order.startReturnRequest();
        } catch (IllegalStateException exception) {
            throw ApiException.conflict(exception.getMessage());
        }
        ReturnCase returnCase = createForOrder(order, customer, request, key);
        if (previous != CustomerOrderStatus.RETURN_REQUESTED) {
            orderHistory.save(new CustomerOrderStatusHistory(order, previous, CustomerOrderStatus.RETURN_REQUESTED,
                    customer, "Return requested " + returnCase.getReturnNumber()));
            orders.saveAndFlush(order);
        }
        linkRequestKey(key, returnCase);
        return ReturnDtos.ReturnResponse.from(loadDetailed(returnCase.getId()));
    }

    @Transactional
    public ReturnDtos.ReturnResponse requestPosSaleReturn(
            long saleId,
            String actorEmail,
            ReturnDtos.CreateReturnRequest request
    ) {
        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow(ApiException::unauthorized);
        String key = request.idempotencyKey().toString();
        String hash = requestHash(ReturnSourceType.POS_SALE, saleId, actor.getId(), request);
        ReturnCase replay = reserveRequest(key, hash);
        if (replay != null) return ReturnDtos.ReturnResponse.from(loadDetailed(replay.getId()));

        PosSale sale = posSales.findDetailedForUpdateById(saleId)
                .orElseThrow(() -> ApiException.notFound("Sale not found"));
        if (sale.getPaymentStatus() != PaymentStatus.PAID
                && sale.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw ApiException.conflict("A fully refunded sale cannot be returned again");
        }
        ReturnCase returnCase = createForSale(sale, actor, request, key);
        linkRequestKey(key, returnCase);
        return ReturnDtos.ReturnResponse.from(loadDetailed(returnCase.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnDtos.ReturnResponse> listCustomerReturns(String email, int page, int size) {
        AppUser customer = users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
        return PageResponse.from(returnCases.findAllByCustomer_IdOrderByRequestedAtDesc(customer.getId(),
                        PageRequest.of(page, size, Sort.by("requestedAt").descending()))
                .map(ReturnDtos.ReturnResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnDtos.ReturnResponse> listReturns(ReturnStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("requestedAt").descending());
        var result = status == null
                ? returnCases.findAllByOrderByRequestedAtDesc(pageable)
                : returnCases.findByStatusOrderByRequestedAtDesc(status, pageable);
        return PageResponse.from(result.map(ReturnDtos.ReturnResponse::from));
    }

    @Transactional(readOnly = true)
    public ReturnDtos.ReturnResponse getReturn(long returnId) {
        return ReturnDtos.ReturnResponse.from(loadDetailed(returnId));
    }

    @Transactional(readOnly = true)
    public ReturnDtos.ReturnResponse getCustomerReturn(long returnId, String email) {
        AppUser customer = users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
        ReturnCase returnCase = returnCases.findDetailedById(returnId)
                .filter(item -> item.getCustomer() != null && item.getCustomer().getId().equals(customer.getId()))
                .orElseThrow(() -> ApiException.notFound("Return request not found"));
        return ReturnDtos.ReturnResponse.from(returnCase);
    }

    @Transactional
    public ReturnDtos.ReturnResponse approve(long returnId, ReturnDtos.ApproveReturnRequest request, String actorEmail) {
        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow(ApiException::unauthorized);
        ReturnCase returnCase = loadDetailedForUpdate(returnId);
        requireStatus(returnCase, ReturnStatus.REQUESTED);
        Map<Long, ReturnDtos.ApprovedLine> decisions = uniqueByItemId(request.items(), ReturnDtos.ApprovedLine::returnItemId);
        if (decisions.size() != returnCase.getItems().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Approval must include every requested return line");
        }
        boolean anyApproved = false;
        for (ReturnCaseItem item : returnCase.getItems()) {
            ReturnDtos.ApprovedLine decision = decisions.get(item.getId());
            if (decision == null || decision.approvedQuantity().compareTo(item.getRequestedQuantity()) > 0
                    || decision.approvedQuantity().stripTrailingZeros().scale() > 3) {
                throw ApiException.conflict("Approved quantity must be between zero and the requested quantity");
            }
            item.approve(decision.approvedQuantity());
            anyApproved |= decision.approvedQuantity().signum() > 0;
        }
        if (!anyApproved) throw ApiException.conflict("At least one return line must be approved");
        returnCase.approve(actor, clock.instant());
        returnCases.saveAndFlush(returnCase);
        return ReturnDtos.ReturnResponse.from(loadDetailed(returnCase.getId()));
    }

    @Transactional
    public ReturnDtos.ReturnResponse reject(long returnId, ReturnDtos.RejectReturnRequest request, String actorEmail) {
        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow(ApiException::unauthorized);
        ReturnCase returnCase = loadDetailedForUpdate(returnId);
        requireStatus(returnCase, ReturnStatus.REQUESTED);
        returnCase.reject(actor, clean(request.reason()), clock.instant());
        returnCases.saveAndFlush(returnCase);
        if (returnCase.getSourceType() == ReturnSourceType.CUSTOMER_ORDER) {
            restoreOrderAfterRejection(returnCase, actor, request.reason().trim());
        }
        return ReturnDtos.ReturnResponse.from(loadDetailed(returnCase.getId()));
    }

    @Transactional
    public ReturnDtos.ReturnResponse inspect(long returnId, ReturnDtos.InspectReturnRequest request, String actorEmail) {
        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow(ApiException::unauthorized);
        ReturnCase returnCase = loadDetailedForUpdate(returnId);
        requireStatus(returnCase, ReturnStatus.APPROVED);
        Map<Long, ReturnDtos.InspectionLine> inspections = uniqueByItemId(request.items(), ReturnDtos.InspectionLine::returnItemId);
        if (inspections.size() != returnCase.getItems().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Inspection must include every return line");
        }
        for (ReturnCaseItem item : returnCase.getItems()) {
            ReturnDtos.InspectionLine line = inspections.get(item.getId());
            if (line == null || line.restockQuantity().signum() < 0 || line.damagedQuantity().signum() < 0
                    || line.scrapQuantity().signum() < 0 || line.restockQuantity().stripTrailingZeros().scale() > 3
                    || line.damagedQuantity().stripTrailingZeros().scale() > 3
                    || line.scrapQuantity().stripTrailingZeros().scale() > 3) {
                throw ApiException.conflict("Inspection quantities are invalid");
            }
            BigDecimal dispositionTotal = line.restockQuantity().add(line.damagedQuantity()).add(line.scrapQuantity());
            if (dispositionTotal.compareTo(item.getApprovedQuantity()) != 0) {
                throw ApiException.conflict("Restock, damaged, and scrap quantities must equal the approved return quantity");
            }
            item.inspect(line.restockQuantity(), line.damagedQuantity(), line.scrapQuantity());
        }

        lockReturnSource(returnCase);
        BigDecimal refundAmount = calculateRefund(returnCase);
        returnCase.inspect(actor, refundAmount, clock.instant());
        returnCases.saveAndFlush(returnCase);
        for (ReturnCaseItem item : returnCase.getItems()) {
            inventory.recordReturnInspectionStock(item.getProduct().getId(), returnCase.getWarehouse().getId(),
                    item.getRestockQuantity(), item.getDamagedQuantity(), actorEmail,
                    movementKey(returnCase.getId(), item.getId(), "restock"),
                    movementKey(returnCase.getId(), item.getId(), "damaged"), returnCase.getReturnNumber());
        }
        if (returnCase.getSourceType() == ReturnSourceType.CUSTOMER_ORDER) {
            boolean hasOtherPending = returnCases.existsByCustomerOrder_IdAndStatusIn(
                    returnCase.getCustomerOrder().getId(), Set.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED));
            recordOrderStatus(returnCase, actor, "Return inspected; refund processing may follow",
                    hasOtherPending ? CustomerOrderStatus.RETURN_REQUESTED : CustomerOrderStatus.RETURNED);
        }
        return ReturnDtos.ReturnResponse.from(loadDetailed(returnCase.getId()));
    }

    @Transactional
    public ReturnDtos.ReturnResponse refund(long returnId, ReturnDtos.RefundRequest request, String actorEmail) {
        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow(ApiException::unauthorized);
        String key = request.idempotencyKey().toString();
        String hash = refundHash(returnId, actor.getId(), request);
        if (refundKeys.reserveIfAbsent(key, hash) == 0) {
            ReturnRefundIdempotencyKey existing = refundKeys.findForUpdate(key)
                    .orElseThrow(() -> new IllegalStateException("Refund idempotency reservation disappeared"));
            if (!existing.getRequestHash().equals(hash)) {
                throw ApiException.conflict("Refund idempotency key was already used for a different request");
            }
            if (existing.getRefund() == null) throw new IllegalStateException("Refund reservation has no refund record");
            return ReturnDtos.ReturnResponse.from(loadDetailed(existing.getRefund().getReturnCase().getId()));
        }

        ReturnCase returnCase = loadDetailedForUpdate(returnId);
        requireStatus(returnCase, ReturnStatus.INSPECTED);
        lockReturnSource(returnCase);
        if (returnCase.getRefundAmount().signum() <= 0) {
            throw ApiException.conflict("This approved return has no refundable amount");
        }
        String reference = clean(request.reference());
        if (request.method() == ReturnRefundMethod.CASH && reference != null) {
            throw ApiException.conflict("Cash refunds cannot include an external reference");
        }
        if (request.method() != ReturnRefundMethod.CASH && reference == null) {
            throw ApiException.conflict("Enter the external refund reference after processing the refund");
        }
        ReturnRefund refund = new ReturnRefund(returnCase, nextRefundNumber(), request.method(),
                returnCase.getRefundAmount(), returnCase.getCurrencyCode(), reference, clean(request.notes()),
                actor, key);
        ReturnRefund savedRefund = refunds.saveAndFlush(refund);
        returnCase.setRefund(savedRefund);
        returnCase.markRefunded();
        returnCases.saveAndFlush(returnCase);
        ReturnRefundIdempotencyKey reservation = refundKeys.findForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("Refund idempotency reservation could not be reloaded"));
        reservation.setRefund(savedRefund);
        refundKeys.saveAndFlush(reservation);
        applySourceRefund(returnCase, actor);
        return ReturnDtos.ReturnResponse.from(loadDetailed(returnCase.getId()));
    }

    private ReturnCase createForOrder(CustomerOrder order, AppUser customer,
                                      ReturnDtos.CreateReturnRequest request, String key) {
        HashMap<Long, CustomerOrderItem> sourceById = new HashMap<>();
        order.getItems().forEach(item -> sourceById.put(item.getId(), item));
        ReturnCase returnCase = new ReturnCase(nextReturnNumber(), ReturnSourceType.CUSTOMER_ORDER,
                order, null, customer, customer, request.reason().trim(), clean(request.notes()),
                order.getCurrencyCode(), key);
        addRequestedItems(returnCase, request.items(), sourceById, null);
        return returnCases.saveAndFlush(returnCase);
    }

    private ReturnCase createForSale(PosSale sale, AppUser actor,
                                     ReturnDtos.CreateReturnRequest request, String key) {
        HashMap<Long, PosSaleItem> sourceById = new HashMap<>();
        sale.getItems().forEach(item -> sourceById.put(item.getId(), item));
        ReturnCase returnCase = new ReturnCase(nextReturnNumber(), ReturnSourceType.POS_SALE,
                null, sale, sale.getCustomer(), actor, request.reason().trim(), clean(request.notes()),
                sale.getCurrencyCode(), key);
        addRequestedItems(returnCase, request.items(), null, sourceById);
        return returnCases.saveAndFlush(returnCase);
    }

    private void addRequestedItems(ReturnCase returnCase, List<ReturnDtos.RequestedLine> requested,
                                   Map<Long, CustomerOrderItem> orderItems, Map<Long, PosSaleItem> posItems) {
        HashSet<Long> sourceIds = new HashSet<>();
        short lineNumber = 1;
        for (ReturnDtos.RequestedLine line : requested) {
            if (line.quantity().signum() <= 0 || line.quantity().stripTrailingZeros().scale() > 3
                    || !sourceIds.add(line.sourceItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Return quantities must be positive, unique, and use at most three decimals");
            }
            if (orderItems != null) {
                CustomerOrderItem source = orderItems.get(line.sourceItemId());
                if (source == null) throw ApiException.notFound("Order item not found");
                BigDecimal alreadyRequested = returnItems.requestedForOrderItem(source.getId(), ReturnStatus.REJECTED);
                if (alreadyRequested.add(line.quantity()).compareTo(source.getQuantity()) > 0) {
                    throw ApiException.conflict("Requested return quantity exceeds the unreturned quantity for " + source.getSku());
                }
                returnCase.addItem(new ReturnCaseItem(lineNumber++, source, line.quantity()));
            } else {
                PosSaleItem source = posItems.get(line.sourceItemId());
                if (source == null) throw ApiException.notFound("Sale item not found");
                BigDecimal alreadyRequested = returnItems.requestedForPosItem(source.getId(), ReturnStatus.REJECTED);
                if (alreadyRequested.add(line.quantity()).compareTo(source.getQuantity()) > 0) {
                    throw ApiException.conflict("Requested return quantity exceeds the unreturned quantity for " + source.getSku());
                }
                returnCase.addItem(new ReturnCaseItem(lineNumber++, source, line.quantity()));
            }
        }
    }

    private ReturnCase reserveRequest(String key, String hash) {
        if (requestKeys.reserveIfAbsent(key, hash) != 0) return null;
        ReturnRequestIdempotencyKey existing = requestKeys.findForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("Return request idempotency reservation disappeared"));
        if (!existing.getRequestHash().equals(hash)) {
            throw ApiException.conflict("Return idempotency key was already used for a different request");
        }
        if (existing.getReturnCase() == null) {
            throw new IllegalStateException("Return idempotency reservation has no return case");
        }
        return existing.getReturnCase();
    }

    private void linkRequestKey(String key, ReturnCase returnCase) {
        ReturnRequestIdempotencyKey reservation = requestKeys.findForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("Return request idempotency reservation could not be reloaded"));
        reservation.setReturnCase(returnCase);
        requestKeys.saveAndFlush(reservation);
    }

    private void restoreOrderAfterRejection(ReturnCase returnCase, AppUser actor, String reason) {
        long orderId = returnCase.getCustomerOrder().getId();
        CustomerOrder order = orders.findDetailedForUpdateById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        CustomerOrderStatus previous = order.getStatus();
        boolean pending = returnCases.existsByCustomerOrder_IdAndStatusIn(orderId,
                Set.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED));
        boolean completed = returnCases.existsByCustomerOrder_IdAndStatusIn(orderId,
                Set.of(ReturnStatus.INSPECTED, ReturnStatus.REFUNDED));
        order.restoreStatusAfterRejectedReturn(pending, completed);
        if (order.getStatus() != previous) {
            orderHistory.save(new CustomerOrderStatusHistory(order, previous, order.getStatus(), actor,
                    "Return rejected: " + reason));
        }
        orders.saveAndFlush(order);
    }

    private void recordOrderStatus(ReturnCase returnCase, AppUser actor, String note, CustomerOrderStatus next) {
        CustomerOrder order = orders.findDetailedForUpdateById(returnCase.getCustomerOrder().getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        CustomerOrderStatus previous = order.getStatus();
        if (next == CustomerOrderStatus.RETURNED || next == CustomerOrderStatus.RETURN_REQUESTED) {
            order.markReturnInspected(next == CustomerOrderStatus.RETURN_REQUESTED);
        }
        if (order.getStatus() != previous) {
            orderHistory.save(new CustomerOrderStatusHistory(order, previous, order.getStatus(), actor, note));
            orders.saveAndFlush(order);
        }
    }

    private BigDecimal calculateRefund(ReturnCase returnCase) {
        BigDecimal subtotal;
        BigDecimal discount;
        BigDecimal tax;
        if (returnCase.getSourceType() == ReturnSourceType.CUSTOMER_ORDER) {
            subtotal = returnCase.getCustomerOrder().getSubtotal();
            discount = returnCase.getCustomerOrder().getDiscountAmount();
            tax = returnCase.getCustomerOrder().getTaxAmount();
        } else {
            subtotal = returnCase.getPosSale().getSubtotal();
            discount = returnCase.getPosSale().getDiscountAmount();
            tax = returnCase.getPosSale().getTaxAmount();
        }
        BigDecimal returnedGross = returnCase.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(item.getApprovedQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
        if (returnedGross.signum() == 0 || subtotal.signum() == 0) return ZERO_MONEY;
        BigDecimal discountShare = discount.multiply(returnedGross)
                .divide(subtotal, 4, RoundingMode.HALF_UP).min(discount);
        BigDecimal returnedTaxable = returnedGross.subtract(discountShare).max(BigDecimal.ZERO)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal taxableSubtotal = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal taxShare = taxableSubtotal.signum() == 0 ? BigDecimal.ZERO
                : tax.multiply(returnedTaxable).divide(taxableSubtotal, 4, RoundingMode.HALF_UP);
        BigDecimal calculated = returnedTaxable.add(taxShare).setScale(4, RoundingMode.HALF_UP);
        BigDecimal refundableTotal = subtotal.subtract(discount).max(BigDecimal.ZERO).add(tax)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal alreadyAllocated = returnCase.getSourceType() == ReturnSourceType.CUSTOMER_ORDER
                ? returnCases.allocatedRefundForOrder(returnCase.getCustomerOrder().getId(), returnCase.getId(),
                    List.of(ReturnStatus.INSPECTED, ReturnStatus.REFUNDED))
                : returnCases.allocatedRefundForPosSale(returnCase.getPosSale().getId(), returnCase.getId(),
                    List.of(ReturnStatus.INSPECTED, ReturnStatus.REFUNDED));
        BigDecimal remaining = refundableTotal.subtract(alreadyAllocated).max(BigDecimal.ZERO);
        return calculated.min(remaining).setScale(4, RoundingMode.HALF_UP);
    }

    private void lockReturnSource(ReturnCase returnCase) {
        if (returnCase.getSourceType() == ReturnSourceType.CUSTOMER_ORDER) {
            orders.findDetailedForUpdateById(returnCase.getCustomerOrder().getId())
                    .orElseThrow(() -> ApiException.notFound("Order not found"));
        } else {
            posSales.findDetailedForUpdateById(returnCase.getPosSale().getId())
                    .orElseThrow(() -> ApiException.notFound("Sale not found"));
        }
    }

    private void applySourceRefund(ReturnCase returnCase, AppUser actor) {
        if (returnCase.getSourceType() == ReturnSourceType.CUSTOMER_ORDER) {
            CustomerOrder order = orders.findDetailedForUpdateById(returnCase.getCustomerOrder().getId())
                    .orElseThrow(() -> ApiException.notFound("Order not found"));
            PaymentStatus nextPaymentStatus = refunds.totalRefundedForOrder(order.getId())
                    .compareTo(order.getSubtotal().subtract(order.getDiscountAmount()).add(order.getTaxAmount())) >= 0
                    ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
            CustomerOrderStatus previous = order.getStatus();
            order.applyRefundStatus(nextPaymentStatus);
            if (order.getStatus() != previous) {
                orderHistory.save(new CustomerOrderStatusHistory(order, previous, order.getStatus(), actor,
                        "Refund recorded for " + returnCase.getReturnNumber()));
            }
            orders.saveAndFlush(order);
        } else {
            PosSale sale = posSales.findDetailedForUpdateById(returnCase.getPosSale().getId())
                    .orElseThrow(() -> ApiException.notFound("Sale not found"));
            PaymentStatus nextPaymentStatus = refunds.totalRefundedForPosSale(sale.getId()).compareTo(sale.getTotal()) >= 0
                    ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
            sale.applyRefundStatus(nextPaymentStatus);
            posSales.saveAndFlush(sale);
        }
    }

    private <T> Map<Long, T> uniqueByItemId(List<T> rows, java.util.function.Function<T, Long> idExtractor) {
        HashMap<Long, T> byId = new HashMap<>();
        for (T row : rows) {
            if (byId.put(idExtractor.apply(row), row) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A return line can appear only once");
            }
        }
        return byId;
    }

    private ReturnCase loadDetailed(long id) {
        return returnCases.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Return request not found"));
    }

    private ReturnCase loadDetailedForUpdate(long id) {
        return returnCases.findDetailedForUpdateById(id).orElseThrow(() -> ApiException.notFound("Return request not found"));
    }

    private void requireStatus(ReturnCase returnCase, ReturnStatus expected) {
        if (returnCase.getStatus() != expected) {
            throw ApiException.conflict("Return request must be " + expected.name().toLowerCase(Locale.ROOT));
        }
    }

    private String requestHash(ReturnSourceType sourceType, long sourceId, long actorId,
                               ReturnDtos.CreateReturnRequest request) {
        String lines = request.items().stream().sorted(Comparator.comparing(ReturnDtos.RequestedLine::sourceItemId))
                .map(line -> line.sourceItemId() + ":" + canonical(line.quantity()))
                .reduce((left, right) -> left + ";" + right).orElse("");
        return digest(sourceType + "|" + sourceId + "|" + actorId + "|" + clean(request.reason()) + "|"
                + (clean(request.notes()) == null ? "" : clean(request.notes())) + "|" + lines);
    }

    private String refundHash(long returnId, long actorId, ReturnDtos.RefundRequest request) {
        return digest(returnId + "|" + actorId + "|" + request.method().name() + "|"
                + (clean(request.reference()) == null ? "" : clean(request.reference())) + "|"
                + (clean(request.notes()) == null ? "" : clean(request.notes())));
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }

    private String movementKey(long returnId, long itemId, String disposition) {
        return UUID.nameUUIDFromBytes(("ims:return:" + returnId + ":" + itemId + ":" + disposition)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String nextReturnNumber() {
        return "RT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String nextRefundNumber() {
        return "RF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String canonical(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
