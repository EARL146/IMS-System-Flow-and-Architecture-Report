package com.ims.api.purchasing;

import com.ims.api.audit.SecurityAuditService;
import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class SupplierPayableService {
    private final SupplierPayableRepository payables;
    private final SupplierPayablePaymentRepository payments;
    private final PurchaseOrderRepository orders;
    private final SupplierCreditNoteRepository creditNotes;
    private final AppUserRepository users;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public SupplierPayableService(SupplierPayableRepository payables,
                                  SupplierPayablePaymentRepository payments,
                                  PurchaseOrderRepository orders,
                                  SupplierCreditNoteRepository creditNotes,
                                  AppUserRepository users,
                                  SecurityAuditService auditService,
                                  Clock clock) {
        this.payables = payables;
        this.payments = payments;
        this.orders = orders;
        this.creditNotes = creditNotes;
        this.users = users;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierPayableDtos.Response> list(int page, int size) {
        return PageResponse.from(payables.findRecent(PageRequest.of(page, size))
                .map(SupplierPayableDtos.Response::from));
    }

    @Transactional(readOnly = true)
    public SupplierPayableDtos.Response get(long id) {
        return SupplierPayableDtos.Response.from(loadDetailed(id));
    }

    @Transactional
    public SupplierPayableDtos.Response createOrReconcile(SupplierPayableDtos.CreateRequest request, String actorEmail) {
        PurchaseOrder order = orders.findDetailedForUpdate(request.purchaseOrderId())
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        BigDecimal receivedValue = order.getReceivedValue();
        if (receivedValue.signum() <= 0) {
            throw ApiException.conflict("A supplier payable can only be generated after goods have been received on the purchase order");
        }
        AppUser actor = requireUser(actorEmail);
        BigDecimal totalCredits = creditNotes.sumAmountByPurchaseOrderId(order.getId());
        if (totalCredits == null) {
            totalCredits = BigDecimal.ZERO;
        }
        BigDecimal appliedCredits = totalCredits.min(receivedValue);

        SupplierPayable existing = payables.findByPurchaseOrder_Id(order.getId()).orElse(null);
        if (existing != null) {
            SupplierPayable locked = payables.findForUpdate(existing.getId()).orElseThrow();
            locked.updateGrossAndCredits(receivedValue, appliedCredits);
            payables.saveAndFlush(locked);
            auditService.record("SUPPLIER_PAYABLE_RECONCILED", actor.getEmail(), "SUPPLIER_PAYABLE",
                    locked.getPayableNumber(), "Reconciled gross=" + receivedValue + ", credits=" + appliedCredits);
            return SupplierPayableDtos.Response.from(loadDetailed(locked.getId()));
        }

        SupplierPayable created = new SupplierPayable(
                nextPayableNumber(),
                order,
                order.getSupplier(),
                clean(request.invoiceReference()),
                order.getCurrencyCode(),
                receivedValue,
                appliedCredits,
                request.dueDate(),
                clean(request.notes()),
                actor
        );
        SupplierPayable saved = payables.saveAndFlush(created);
        auditService.record("SUPPLIER_PAYABLE_CREATED", actor.getEmail(), "SUPPLIER_PAYABLE",
                saved.getPayableNumber(), "Created payable for PO " + order.getOrderNumber() + " gross=" + receivedValue + ", credits=" + appliedCredits);
        return SupplierPayableDtos.Response.from(loadDetailed(saved.getId()));
    }

    @Transactional
    public SupplierPayableDtos.Response reconcileCredits(long id, String actorEmail) {
        SupplierPayable payable = loadForUpdate(id);
        PurchaseOrder order = orders.findDetailedById(payable.getPurchaseOrder().getId())
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        BigDecimal receivedValue = order.getReceivedValue();
        BigDecimal totalCredits = creditNotes.sumAmountByPurchaseOrderId(order.getId());
        if (totalCredits == null) {
            totalCredits = BigDecimal.ZERO;
        }
        try {
            payable.updateGrossAndCredits(receivedValue, totalCredits);
        } catch (IllegalStateException ex) {
            throw ApiException.conflict(ex.getMessage());
        }
        payables.saveAndFlush(payable);
        AppUser actor = requireUser(actorEmail);
        auditService.record("SUPPLIER_PAYABLE_RECONCILED", actor.getEmail(), "SUPPLIER_PAYABLE",
                payable.getPayableNumber(), "Reconciled credits=" + payable.getCreditAppliedAmount());
        return SupplierPayableDtos.Response.from(loadDetailed(id));
    }

    @Transactional
    public SupplierPayableDtos.Response recordPayment(long id, SupplierPayableDtos.PaymentRequest request, String actorEmail) {
        SupplierPayable payable = loadForUpdate(id);
        String key = request.idempotencyKey().toString();
        String requestHash = paymentRequestHash(id, request);
        SupplierPayablePayment existingPayment = payments.findByIdempotencyKey(key).orElse(null);
        if (existingPayment != null) {
            if (!existingPayment.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict("Supplier payable payment idempotency key was already used for a different request");
            }
            return SupplierPayableDtos.Response.from(loadDetailed(existingPayment.getPayable().getId()));
        }

        // First auto-reconcile any newly recorded supplier credit notes
        PurchaseOrder order = orders.findDetailedById(payable.getPurchaseOrder().getId())
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        BigDecimal totalCredits = creditNotes.sumAmountByPurchaseOrderId(order.getId());
        if (totalCredits != null) {
            payable.updateGrossAndCredits(order.getReceivedValue(), totalCredits);
        }

        AppUser actor = requireUser(actorEmail);
        SupplierPayablePayment payment = new SupplierPayablePayment(
                request.paymentReference().trim(),
                request.paymentMethod(),
                request.amount(),
                payable.getCurrencyCode(),
                clean(request.notes()),
                key,
                requestHash,
                actor,
                clock.instant()
        );
        try {
            payable.addPayment(payment);
        } catch (IllegalStateException ex) {
            throw ApiException.conflict(ex.getMessage());
        }
        payables.saveAndFlush(payable);
        auditService.record("SUPPLIER_PAYABLE_PAYMENT", actor.getEmail(), "SUPPLIER_PAYABLE",
                payable.getPayableNumber(), "Recorded supplier payment " + request.amount() + " " + payable.getCurrencyCode() + " ref=" + request.paymentReference().trim());
        return SupplierPayableDtos.Response.from(loadDetailed(id));
    }

    private SupplierPayable loadDetailed(long id) {
        return payables.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("Supplier payable not found"));
    }

    private SupplierPayable loadForUpdate(long id) {
        return payables.findForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Supplier payable not found"));
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
    }

    private String nextPayableNumber() {
        return "AP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    private String paymentRequestHash(long id, SupplierPayableDtos.PaymentRequest request) {
        String canonical = id + "|" + request.paymentReference().trim() + "|"
                + request.paymentMethod().name() + "|"
                + request.amount().stripTrailingZeros().toPlainString() + "|"
                + (clean(request.notes()) == null ? "" : clean(request.notes()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
