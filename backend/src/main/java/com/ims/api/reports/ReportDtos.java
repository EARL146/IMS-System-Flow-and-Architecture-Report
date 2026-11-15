package com.ims.api.reports;

import java.math.BigDecimal;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record SalesSummary(
            long posSalesCount,
            BigDecimal posRevenue,
            long ecommerceOrderCount,
            BigDecimal ecommerceRevenue,
            long storePickupOrderCount,
            long deliveryOrderCount,
            BigDecimal totalRevenue
    ) {
    }

    public record LedgerDiscrepancyRow(
            long productId,
            String sku,
            long warehouseId,
            String warehouseCode,
            BigDecimal balanceOnHand,
            BigDecimal ledgerExpectedOnHand,
            BigDecimal variance
    ) {
    }

    public record InventorySummary(
            long totalBalanceRows,
            BigDecimal totalOnHandUnits,
            BigDecimal totalReservedUnits,
            BigDecimal totalDamagedUnits,
            BigDecimal totalAvailableUnits,
            BigDecimal totalRetailValuation,
            long lowStockRows,
            boolean ledgerReconciled,
            int discrepancyCount,
            List<LedgerDiscrepancyRow> discrepancies
    ) {
    }

    public record PayablesSummary(
            long totalPayablesCount,
            long unsettledPayablesCount,
            BigDecimal totalGrossAmount,
            BigDecimal totalCreditApplied,
            BigDecimal totalAmountPaid,
            BigDecimal totalBalanceDue
    ) {
    }

    public record SupplierPerformanceRow(
            long supplierId,
            String supplierCode,
            String supplierName,
            long purchaseOrderCount,
            BigDecimal totalOrderedValue,
            BigDecimal totalReceivedValue,
            long supplierReturnCount,
            BigDecimal totalReturnedValue,
            BigDecimal totalCreditNotesValue,
            BigDecimal receiptFulfillmentRatePercent
    ) {
    }

    public record OverviewResponse(
            SalesSummary sales,
            InventorySummary inventory,
            PayablesSummary payables,
            List<SupplierPerformanceRow> supplierPerformance
    ) {
    }
}
