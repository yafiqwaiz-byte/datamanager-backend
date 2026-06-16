package dev.waiz.datamanager.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PO_aging_raw")
public class poagingraw {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID rowId;

    @ManyToOne
    @JoinColumn(name ="report_id")
    private poagingreport report;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "po_item")
    private String poItem;

    @Column(name = "po_description")
    private String poDescription;

    @Column(name = "bus_area")
    private String busArea;

    @Column(name = "purch_group")
    private String purchGroup;

    @Column(name = "company_code")
    private String companyCode;

    @Column(name = "company_name")
    private String companyName;
// ── Vendor Info ────────────────────────────────────────────────
    @Column(name = "vendor_acc_no")
    private String vendorAccNo;

    @Column(name = "vendor_name")
    private String vendorName;

    // ── Dates ──────────────────────────────────────────────────────
    @Column(name = "created_date")
    private String createdDate;

    @Column(name = "item_delivery_date")
    private String itemDeliveryDate;

    @Column(name = "validity_start")
    private String validityStart;

    @Column(name = "validity_end")
    private String validityEnd;

    // ── Requisitioner Info ─────────────────────────────────────────
    @Column(name = "requisitioner")
    private String requisitioner;

    @Column(name = "requisitioner_name")
    private String requisitionerName;

    @Column(name = "requisitioner_email")
    private String requisitionerEmail;

    @Column(name = "requisitioner_department")
    private String requisitionerDepartment;

    @Column(name = "requisitioner_division")
    private String requisitionerDivision;

   // ── PR Creator Info ────────────────────────────────────────────
    @Column(name = "pr_creator")
    private String prCreator;

    @Column(name = "pr_creator_name")
    private String prCreatorName;

    @Column(name = "pr_creator_email")
    private String prCreatorEmail;

    @Column(name = "pr_creator_department")
    private String prCreatorDepartment;

    @Column(name = "pr_creator_division")
    private String prCreatorDivision;

    // ── Outline Agreement ──────────────────────────────────────────
    @Column(name = "outline_agreement_no")
    private String outlineAgreementNo;

    @Column(name = "outline_agreement_item")
    private String outlineAgreementItem;

    @Column(name = "outline_agreement_description", columnDefinition = "TEXT")
    private String outlineAgreementDescription;

    // ── Financial ──────────────────────────────────────────────────
    @Column(name = "net_order_value")
    private Double netOrderValue;

    @Column(name = "currency")
    private String currency;

    @Column(name = "exchange_rate")
    private Double exchangeRate;

    @Column(name = "outstanding_po_value")
    private Double outstandingPOValue;      // ← Key for amount

    @Column(name = "tracking_no")
    private String trackingNo;

    @Column(name = "held_po")
    private String heldPO;


    // ── Key Aging Fields ───────────────────────────────────────────
    @Column(name = "no_of_days_outstanding")
    private Integer noOfDaysOutstanding;    // ← Filter > 180

    @Column(name = "is_cleared")
    @Builder.Default
    private Boolean isCleared = false;      // ← From CLEARED? column

    @Column(name = "cleared_at")
    private OffsetDateTime clearedAt;


}
