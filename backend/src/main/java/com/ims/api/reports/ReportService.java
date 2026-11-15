package com.ims.api.reports;

import com.ims.api.audit.SecurityAuditEvent;
import com.ims.api.audit.SecurityAuditEventRepository;
import com.ims.api.common.ApiException;
import com.ims.api.customer.CustomerOrder;
import com.ims.api.customer.CustomerOrderRepository;
import com.ims.api.customer.CustomerOrderStatus;
import com.ims.api.customer.FulfillmentType;
import com.ims.api.customer.PaymentStatus;
import com.ims.api.inventory.InventoryBalance;
import com.ims.api.inventory.InventoryBalanceRepository;
import com.ims.api.inventory.InventoryMovement;
import com.ims.api.inventory.InventoryMovementRepository;
import com.ims.api.pos.PosSale;
import com.ims.api.pos.PosSaleRepository;
import com.ims.api.purchasing.PurchaseOrder;
import com.ims.api.purchasing.PurchaseOrderRepository;
import com.ims.api.purchasing.Supplier;
import com.ims.api.purchasing.SupplierCreditNote;
import com.ims.api.purchasing.SupplierPayable;
import com.ims.api.purchasing.SupplierPayableRepository;
import com.ims.api.purchasing.SupplierPayableStatus;
import com.ims.api.purchasing.SupplierRepository;
import com.ims.api.purchasing.SupplierReturn;
import com.ims.api.purchasing.SupplierReturnRepository;
import com.ims.api.purchasing.SupplierReturnStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportService {
    private final InventoryBalanceRepository balances;
    private final InventoryMovementRepository movements;
    private final PosSaleRepository posSales;
    private final CustomerOrderRepository customerOrders;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository purchaseOrders;
    private final SupplierReturnRepository supplierReturns;
    private final SupplierPayableRepository supplierPayables;
    private final SecurityAuditEventRepository auditEvents;

    public ReportService(InventoryBalanceRepository balances,
                         InventoryMovementRepository movements,
                         PosSaleRepository posSales,
                         CustomerOrderRepository customerOrders,
                         SupplierRepository suppliers,
                         PurchaseOrderRepository purchaseOrders,
                         SupplierReturnRepository supplierReturns,
                         SupplierPayableRepository supplierPayables,
                         SecurityAuditEventRepository auditEvents) {
        this.balances = balances;
        this.movements = movements;
        this.posSales = posSales;
        this.customerOrders = customerOrders;
        this.suppliers = suppliers;
        this.purchaseOrders = purchaseOrders;
        this.supplierReturns = supplierReturns;
        this.supplierPayables = supplierPayables;
        this.auditEvents = auditEvents;
    }

    @Transactional(readOnly = true)
    public ReportDtos.OverviewResponse overview() {
        return new ReportDtos.OverviewResponse(
                buildSalesSummary(),
                buildInventorySummary(),
                buildPayablesSummary(),
                buildSupplierPerformance()
        );
    }

    @Transactional(readOnly = true)
    public String exportCsv(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "inventory" -> buildInventoryCsv();
            case "sales" -> buildSalesCsv();
            case "suppliers" -> buildSupplierCsv();
            case "audit" -> buildAuditCsv();
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported export report type: " + type);
        };
    }

    private ReportDtos.SalesSummary buildSalesSummary() {
        List<PosSale> allPos = posSales.findAll();
        long posCount = 0;
        BigDecimal posRevenue = BigDecimal.ZERO;
        for (PosSale sale : allPos) {
            if (sale.getPaymentStatus() != PaymentStatus.REFUNDED) {
                posCount++;
                posRevenue = posRevenue.add(sale.getTotal());
            }
        }

        List<CustomerOrder> allOrders = customerOrders.findAll();
        long orderCount = 0;
        long pickupCount = 0;
        long deliveryCount = 0;
        BigDecimal orderRevenue = BigDecimal.ZERO;
        for (CustomerOrder order : allOrders) {
            if (order.getStatus() != CustomerOrderStatus.CANCELLED) {
                orderCount++;
                orderRevenue = orderRevenue.add(order.getTotal());
                if (order.getFulfillmentType() == FulfillmentType.STORE_PICKUP) {
                    pickupCount++;
                } else {
                    deliveryCount++;
                }
            }
        }

        return new ReportDtos.SalesSummary(
                posCount,
                posRevenue,
                orderCount,
                orderRevenue,
                pickupCount,
                deliveryCount,
                posRevenue.add(orderRevenue)
        );
    }

    private ReportDtos.InventorySummary buildInventorySummary() {
        List<InventoryBalance> allBalances = balances.findAllDetailed();
        List<InventoryMovement> allMovements = movements.findAllOrdered();

        record Key(long productId, long warehouseId) {}
        Map<Key, BigDecimal> initialBeforeByKey = new HashMap<>();
        Map<Key, BigDecimal> sumDeltaByKey = new HashMap<>();
        for (InventoryMovement movement : allMovements) {
            Key key = new Key(movement.getProduct().getId(), movement.getWarehouse().getId());
            initialBeforeByKey.putIfAbsent(key, movement.getQuantityBefore());
            sumDeltaByKey.merge(key, movement.getQuantityDelta(), BigDecimal::add);
        }

        BigDecimal totalOnHand = BigDecimal.ZERO;
        BigDecimal totalReserved = BigDecimal.ZERO;
        BigDecimal totalDamaged = BigDecimal.ZERO;
        BigDecimal totalAvailable = BigDecimal.ZERO;
        BigDecimal totalValuation = BigDecimal.ZERO;
        long lowStockRows = 0;
        List<ReportDtos.LedgerDiscrepancyRow> discrepancies = new ArrayList<>();

        for (InventoryBalance balance : allBalances) {
            totalOnHand = totalOnHand.add(balance.getOnHandQuantity());
            totalReserved = totalReserved.add(balance.getReservedQuantity());
            totalDamaged = totalDamaged.add(balance.getDamagedQuantity());
            BigDecimal available = balance.getAvailableQuantity();
            totalAvailable = totalAvailable.add(available);
            totalValuation = totalValuation.add(balance.getOnHandQuantity().multiply(balance.getProduct().getSellingPrice()));
            if (available.compareTo(balance.getProduct().getReorderLevel()) <= 0) {
                lowStockRows++;
            }

            Key key = new Key(balance.getProduct().getId(), balance.getWarehouse().getId());
            if (initialBeforeByKey.containsKey(key)) {
                BigDecimal expectedOnHand = initialBeforeByKey.get(key)
                        .add(sumDeltaByKey.getOrDefault(key, BigDecimal.ZERO));
                if (expectedOnHand.compareTo(balance.getOnHandQuantity()) != 0) {
                    discrepancies.add(new ReportDtos.LedgerDiscrepancyRow(
                            balance.getProduct().getId(),
                            balance.getProduct().getSku(),
                            balance.getWarehouse().getId(),
                            balance.getWarehouse().getCode(),
                            balance.getOnHandQuantity(),
                            expectedOnHand,
                            balance.getOnHandQuantity().subtract(expectedOnHand)
                    ));
                }
            }
        }

        return new ReportDtos.InventorySummary(
                allBalances.size(),
                totalOnHand,
                totalReserved,
                totalDamaged,
                totalAvailable,
                totalValuation,
                lowStockRows,
                discrepancies.isEmpty(),
                discrepancies.size(),
                discrepancies
        );
    }

    private ReportDtos.PayablesSummary buildPayablesSummary() {
        List<SupplierPayable> allPayables = supplierPayables.findRecent(PageRequest.of(0, 1000)).getContent();
        long unsettled = 0;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        for (SupplierPayable payable : allPayables) {
            if (payable.getStatus() == SupplierPayableStatus.CANCELLED) {
                continue;
            }
            if (payable.getStatus() != SupplierPayableStatus.SETTLED) {
                unsettled++;
            }
            gross = gross.add(payable.getGrossAmount());
            credits = credits.add(payable.getCreditAppliedAmount());
            paid = paid.add(payable.getAmountPaid());
            due = due.add(payable.getBalanceDue());
        }
        return new ReportDtos.PayablesSummary(allPayables.size(), unsettled, gross, credits, paid, due);
    }

    private List<ReportDtos.SupplierPerformanceRow> buildSupplierPerformance() {
        List<Supplier> allSuppliers = suppliers.findAll();
        List<PurchaseOrder> allOrders = purchaseOrders.findAllDetailed();
        List<SupplierReturn> allReturns = supplierReturns.findAllDetailed();

        List<ReportDtos.SupplierPerformanceRow> rows = new ArrayList<>();
        for (Supplier supplier : allSuppliers) {
            long poCount = 0;
            BigDecimal orderedValue = BigDecimal.ZERO;
            BigDecimal receivedValue = BigDecimal.ZERO;
            for (PurchaseOrder order : allOrders) {
                if (order.getSupplier().getId().equals(supplier.getId())) {
                    poCount++;
                    orderedValue = orderedValue.add(order.getTotal());
                    receivedValue = receivedValue.add(order.getReceivedValue());
                }
            }
            long returnCount = 0;
            BigDecimal returnedValue = BigDecimal.ZERO;
            BigDecimal creditNotesValue = BigDecimal.ZERO;
            for (SupplierReturn sr : allReturns) {
                if (sr.getPurchaseOrder().getSupplier().getId().equals(supplier.getId())
                        && sr.getStatus() != SupplierReturnStatus.REJECTED
                        && sr.getStatus() != SupplierReturnStatus.CANCELLED) {
                    returnCount++;
                    returnedValue = returnedValue.add(sr.getExpectedCreditAmount());
                    BigDecimal credited = sr.getCreditNotes().stream()
                            .map(SupplierCreditNote::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    creditNotesValue = creditNotesValue.add(credited);
                }
            }
            BigDecimal fulfillmentRate = orderedValue.signum() > 0
                    ? receivedValue.multiply(new BigDecimal("100")).divide(orderedValue, 2, RoundingMode.HALF_UP)
                    : new BigDecimal("100.00");
            rows.add(new ReportDtos.SupplierPerformanceRow(
                    supplier.getId(),
                    supplier.getCode(),
                    supplier.getName(),
                    poCount,
                    orderedValue,
                    receivedValue,
                    returnCount,
                    returnedValue,
                    creditNotesValue,
                    fulfillmentRate
            ));
        }
        return rows;
    }

    private String buildInventoryCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Warehouse Code,Warehouse Name,SKU,Product Name,On Hand,Reserved,Damaged,Available,Unit Price,Retail Valuation\n");
        for (InventoryBalance b : balances.findAllDetailed()) {
            BigDecimal valuation = b.getOnHandQuantity().multiply(b.getProduct().getSellingPrice());
            sb.append(csvCell(b.getWarehouse().getCode())).append(',')
                    .append(csvCell(b.getWarehouse().getName())).append(',')
                    .append(csvCell(b.getProduct().getSku())).append(',')
                    .append(csvCell(b.getProduct().getName())).append(',')
                    .append(b.getOnHandQuantity().toPlainString()).append(',')
                    .append(b.getReservedQuantity().toPlainString()).append(',')
                    .append(b.getDamagedQuantity().toPlainString()).append(',')
                    .append(b.getAvailableQuantity().toPlainString()).append(',')
                    .append(b.getProduct().getSellingPrice().toPlainString()).append(',')
                    .append(valuation.toPlainString()).append('\n');
        }
        return sb.toString();
    }

    private String buildSalesCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Channel,Reference Number,Fulfillment,Status,Payment Method,Total Amount,Currency,Created At\n");
        for (PosSale sale : posSales.findAll()) {
            sb.append("POS").append(',')
                    .append(csvCell(sale.getSaleNumber())).append(',')
                    .append("IN_STORE").append(',')
                    .append(sale.getPaymentStatus().name()).append(',')
                    .append(sale.getPaymentMethod().name()).append(',')
                    .append(sale.getTotal().toPlainString()).append(',')
                    .append(csvCell(sale.getCurrencyCode())).append(',')
                    .append(sale.getCreatedAt()).append('\n');
        }
        for (CustomerOrder order : customerOrders.findAll()) {
            sb.append("ECOMMERCE").append(',')
                    .append(csvCell(order.getOrderNumber())).append(',')
                    .append(order.getFulfillmentType().name()).append(',')
                    .append(order.getStatus().name()).append(',')
                    .append(order.getPaymentMethod().name()).append(',')
                    .append(order.getTotal().toPlainString()).append(',')
                    .append(csvCell(order.getCurrencyCode())).append(',')
                    .append(order.getCreatedAt()).append('\n');
        }
        return sb.toString();
    }

    private String buildSupplierCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Supplier Code,Supplier Name,PO Count,Ordered Value,Received Value,Return Count,Returned Value,Credit Notes Value,Fulfillment Rate %\n");
        for (ReportDtos.SupplierPerformanceRow row : buildSupplierPerformance()) {
            sb.append(csvCell(row.supplierCode())).append(',')
                    .append(csvCell(row.supplierName())).append(',')
                    .append(row.purchaseOrderCount()).append(',')
                    .append(row.totalOrderedValue().toPlainString()).append(',')
                    .append(row.totalReceivedValue().toPlainString()).append(',')
                    .append(row.supplierReturnCount()).append(',')
                    .append(row.totalReturnedValue().toPlainString()).append(',')
                    .append(row.totalCreditNotesValue().toPlainString()).append(',')
                    .append(row.receiptFulfillmentRatePercent().toPlainString()).append('\n');
        }
        return sb.toString();
    }

    private String buildAuditCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Event Type,Actor Email,Target Type,Target Id,Summary,Occurred At\n");
        List<SecurityAuditEvent> recent = auditEvents.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, 500)).getContent();
        for (SecurityAuditEvent event : recent) {
            sb.append(csvCell(event.getEventType())).append(',')
                    .append(csvCell(event.getActorEmail())).append(',')
                    .append(csvCell(event.getTargetType())).append(',')
                    .append(csvCell(event.getTargetId())).append(',')
                    .append(csvCell(event.getSummary())).append(',')
                    .append(event.getOccurredAt()).append('\n');
        }
        return sb.toString();
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value;
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                sanitized = "'" + sanitized;
            }
        }
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }
}
