package dev.waiz.datamanager.service;

import java.util.*;
import java.util.regex.*;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * RegexValidationService
 *
 * Validates individual placeholder values extracted from OCR text.
 * Called AFTER field mapping (Pass 0/1/2) and BEFORE business validation.
 *
 * Returns a ValidationResult per field so the caller can decide
 * whether to auto-correct, flag for staff review, or reject.
 */
@Service
@Slf4j
public class RegexValidationService {

    // ══════════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════════

    /**
     * Validate all mapped fields and return per-field results.
     *
     * @param mappedFields  placeholder → extracted value
     * @return              placeholder → ValidationResult
     */
    public Map<String, ValidationResult> validateAll(Map<String, String> mappedFields) {
        Map<String, ValidationResult> results = new LinkedHashMap<>();

        mappedFields.forEach((placeholder, value) -> {
            ValidationResult result = validateField(placeholder, value);
            results.put(placeholder, result);
            if (!result.isValid()) {
                log.warn("Validation failed — {}: '{}' → {}", placeholder, value, result.getMessage());
            } else {
                log.info("Validation passed — {}: '{}'", placeholder, value);
            }
        });

        return results;
    }

    /**
     * Returns true only if ALL fields passed validation.
     */
    public boolean isAllValid(Map<String, ValidationResult> results) {
    return results.values().stream()
        .allMatch(r -> r != null && r.isValid());
    }

    /**
     * Returns only the fields that failed validation.
     */
    public Map<String, ValidationResult> getFailures(Map<String, ValidationResult> results) {
        Map<String, ValidationResult> failures = new LinkedHashMap<>();
        results.forEach((k, v) -> {
            if (!v.isValid()) failures.put(k, v);
        });
        return failures;
    }

    // ══════════════════════════════════════════════════════════════
    //  Field dispatcher
    // ══════════════════════════════════════════════════════════════

    private ValidationResult validateField(String placeholder, String value) {

        // Empty/null check — skip format validation for optional empty fields
        if (value == null || value.isBlank()) {
            return isRequired(placeholder)
                ? ValidationResult.fail("Required field is empty")
                : ValidationResult.skip("Field is empty — optional");
        }

        String trimmed = value.trim();

        return switch (placeholder) {

            // ── Reference number ──────────────────────────────────
            case "[RUJUKAN_KAMI]"        -> validateRujukan(trimmed);

            // ── Dates ─────────────────────────────────────────────
            case "[TARIKH]",
                 "[TARIKH_SURAT]"        -> validateMalayDate(trimmed);

            case "[TARIKH_MULA_BERKHIDMAT]",
                 "[TARIKH_MULA]",
                 "[TARIKH_TAMAT]",
                 "[TARIKH_AKHIR]"        -> validateFlexibleDate(trimmed);

            // ── Person fields ──────────────────────────────────────
            case "[NAMA_PEKERJA]",
                 "[NAMA_KETUA]",
                 "[NAMA]"               -> validateName(trimmed);

            // ── Staff ID ───────────────────────────────────────────
            case "[NO_PEKERJA]",
                 "[NO_KAKITANGAN]"       -> validateStaffId(trimmed);

            // ── Malaysian IC ───────────────────────────────────────
            case "[NO_KP]",
                 "[NO_PENGENALAN]",
                 "[NO_K_PENGENALAN]"     -> validateMyKad(trimmed);

            // ── Position / unit / department ───────────────────────
            case "[JAWATAN_PEKERJA]",
                 "[JAWATAN_KETUA]",
                 "[JAWATAN]",
                 "[UNIT]",
                 "[BAHAGIAN]",
                 "[JABATAN]"             -> validateNotEmpty(trimmed, placeholder);

            // ── Duration e.g. "11 tahun" ───────────────────────────
            case "[TEMPOH_BERKHIDMAT]",
                 "[TEMPOH]"              -> validateTempoh(trimmed);

            // ── Grade e.g. "TT10", "F41" ──────────────────────────
            case "[GRED]"               -> validateGred(trimmed);

            // ── Phone ──────────────────────────────────────────────
            case "[NO_TEL]",
                 "[TELEFON]"             -> validatePhone(trimmed);

            // ── Unknown placeholder — pass through ─────────────────
            default                     -> ValidationResult.pass("No specific rule — accepted as-is");
        };
    }

    // ══════════════════════════════════════════════════════════════
    //  Individual validators
    // ══════════════════════════════════════════════════════════════

    /**
     * Rujukan: TNB/DN/SBUAD/SERVIS/NOR 21/4
     * Pattern: alphanumeric segments separated by / with optional spaces
     */
    private ValidationResult validateRujukan(String value) {
        // e.g. TNB/DN/SBUAD/SERVIS/NOR 21/4
        Pattern p = Pattern.compile(
            "^[A-Z0-9]+(/[A-Z0-9]+)+([\\s/][0-9]+)?$",
            Pattern.CASE_INSENSITIVE);

        if (p.matcher(value.replace(" ", "")).matches()) {
            return ValidationResult.pass("Valid reference number format");
        }

        // Soft warning — reference formats vary; don't hard-fail
        return ValidationResult.warn(
            "Unusual reference format — expected pattern like 'TNB/XX/XXX/NOR 21/4'. " +
            "Please verify: '" + value + "'");
    }

    /**
     * Malay date: "24 Julai 2025", "1 Januari 2026"
     */
    private ValidationResult validateMalayDate(String value) {
        String malayMonths =
            "Januari|Februari|Mac|April|Mei|Jun|Julai|" +
            "Ogos|September|Oktober|November|Disember";

        Pattern p = Pattern.compile(
            "^(0?[1-9]|[12][0-9]|3[01])\\s+(" + malayMonths + ")\\s+(19|20)\\d{2}$",
            Pattern.CASE_INSENSITIVE);

        if (p.matcher(value).matches()) {
            return ValidationResult.pass("Valid Malay date format");
        }

        // Also accept numeric date formats as fallback
        return validateFlexibleDate(value);
    }

    /**
     * Flexible date: dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd, d/M/yyyy
     */
    private ValidationResult validateFlexibleDate(String value) {
        List<Pattern> patterns = List.of(
            Pattern.compile("^(0?[1-9]|[12][0-9]|3[01])[/\\-](0?[1-9]|1[0-2])[/\\-](19|20)\\d{2}$"),
            Pattern.compile("^(19|20)\\d{2}[/\\-](0?[1-9]|1[0-2])[/\\-](0?[1-9]|[12][0-9]|3[01])$"),
            Pattern.compile("^(0?[1-9]|[12][0-9]|3[01])\\s+(Januari|Februari|Mac|April|Mei|Jun|Julai|Ogos|September|Oktober|November|Disember)\\s+(19|20)\\d{2}$",
                Pattern.CASE_INSENSITIVE)
        );

        for (Pattern p : patterns) {
            if (p.matcher(value).matches()) {
                return ValidationResult.pass("Valid date format");
            }
        }

        return ValidationResult.fail(
            "Invalid date format: '" + value + "'. " +
            "Expected: 'dd/MM/yyyy', 'dd-MM-yyyy', or '24 Julai 2025'");
    }

    /**
     * Full name: must contain at least 2 words, letters only (allow bin/binti/a/l/a/p)
     * e.g. "MUHAMMAD SHAH AZMIL BIN SHARIFUDDIN", "Mohd Rafie Bin Mohd Khanafie"
     */
    private ValidationResult validateName(String value) {
        // Must have at least 2 words
        String[] words = value.trim().split("\\s+");
        if (words.length < 2) {
            return ValidationResult.fail(
                "Name appears incomplete — expected full name with at least 2 words: '" + value + "'");
        }

        // Must not contain numbers or special characters (except apostrophe, hyphen)
        Pattern invalidChars = Pattern.compile("[^a-zA-Z\\s'\\-./]");
        if (invalidChars.matcher(value).find()) {
            return ValidationResult.warn(
                "Name contains unusual characters — possible OCR error: '" + value + "'");
        }

        // Check for common OCR errors (numbers replacing letters)
        Pattern ocrNoise = Pattern.compile("[0-9]");
        if (ocrNoise.matcher(value).find()) {
            return ValidationResult.warn(
                "Name contains digits — possible OCR noise (e.g. '0'→'O'): '" + value + "'");
        }

        return ValidationResult.pass("Valid name format");
    }

    /**
     * Staff ID: numeric, typically 6-10 digits
     * e.g. "10096824"
     */
    private ValidationResult validateStaffId(String value) {
        Pattern p = Pattern.compile("^\\d{6,10}$");
        if (p.matcher(value).matches()) {
            return ValidationResult.pass("Valid staff ID format");
        }
        return ValidationResult.fail(
            "Invalid staff ID — expected 6-10 digits: '" + value + "'");
    }

    /**
     * Malaysian IC (MyKad): YYMMDD-PB-XXXX
     * e.g. "900619-08-6009"
     */
    private ValidationResult validateMyKad(String value) {
        // Accept with or without dashes
        String normalized = value.replace("-", "").replace(" ", "");

        Pattern p = Pattern.compile("^\\d{12}$");
        if (!p.matcher(normalized).matches()) {
            return ValidationResult.fail(
                "Invalid MyKad format — expected YYMMDD-PB-XXXX (12 digits): '" + value + "'");
        }

        // Validate birth month (digits 3-4)
        int month = Integer.parseInt(normalized.substring(2, 4));
        if (month < 1 || month > 12) {
            return ValidationResult.fail(
                "Invalid MyKad — birth month out of range (01-12): '" + value + "'");
        }

        // Validate birth day (digits 5-6)
        int day = Integer.parseInt(normalized.substring(4, 6));
        if (day < 1 || day > 31) {
            return ValidationResult.fail(
                "Invalid MyKad — birth day out of range (01-31): '" + value + "'");
        }

        // Validate place code (digits 7-8): 01-16 (states) or specific codes
        int placeCode = Integer.parseInt(normalized.substring(6, 8));
        if (placeCode < 1 || placeCode > 59) {
            return ValidationResult.warn(
                "Unusual MyKad place code '" + String.format("%02d", placeCode) +
                "' — please verify: '" + value + "'");
        }

        return ValidationResult.pass("Valid MyKad format");
    }

    /**
     * Duration: "11 tahun", "2 tahun 3 bulan", "11 years"
     */
    private ValidationResult validateTempoh(String value) {
        Pattern p = Pattern.compile(
            "^\\d+\\s+(tahun|year|bulan|month)(\\s+\\d+\\s+(bulan|month))?$",
            Pattern.CASE_INSENSITIVE);

        if (p.matcher(value).matches()) {
            return ValidationResult.pass("Valid duration format");
        }

        return ValidationResult.warn(
            "Unusual duration format — expected e.g. '11 tahun': '" + value + "'");
    }

    /**
     * Grade: "TT10", "F41", "N17", "JUSA C"
     */
    private ValidationResult validateGred(String value) {
        Pattern p = Pattern.compile(
            "^(JUSA\\s+[ABC]|[A-Z]{1,3}\\d{1,2}|Gred\\s+[A-Z]{1,3}\\d{1,2})$",
            Pattern.CASE_INSENSITIVE);

        if (p.matcher(value).matches()) {
            return ValidationResult.pass("Valid grade format");
        }

        return ValidationResult.warn(
            "Unusual grade format — expected e.g. 'TT10', 'F41', 'JUSA C': '" + value + "'");
    }

    /**
     * Phone: Malaysian format — 01X-XXXXXXX or 0X-XXXXXXX
     */
    private ValidationResult validatePhone(String value) {
        String normalized = value.replace("-", "").replace(" ", "");
        Pattern p = Pattern.compile("^(\\+?60|0)[1-9]\\d{7,9}$");

        if (p.matcher(normalized).matches()) {
            return ValidationResult.pass("Valid Malaysian phone format");
        }

        return ValidationResult.warn(
            "Unusual phone format — expected Malaysian number: '" + value + "'");
    }

    /**
     * Generic not-empty check for free-text fields.
     */
    private ValidationResult validateNotEmpty(String value, String placeholder) {
        if (value.isBlank()) {
            return ValidationResult.fail("Required field '" + placeholder + "' is empty");
        }
        if (value.length() < 2) {
            return ValidationResult.warn("Value seems too short for '" + placeholder + "': '" + value + "'");
        }
        return ValidationResult.pass("Non-empty value accepted");
    }

    // ══════════════════════════════════════════════════════════════
    //  Required field list
    // ══════════════════════════════════════════════════════════════

    private boolean isRequired(String placeholder) {
        Set<String> required = Set.of(
            "[NAMA_PEKERJA]",
            "[NO_PEKERJA]",
            "[NO_KP]",
            "[TARIKH]",
            "[NAMA_KETUA]",
            "[JAWATAN_KETUA]"
        );
        return required.contains(placeholder);
    }

    // ══════════════════════════════════════════════════════════════
    //  ValidationResult inner class
    // ══════════════════════════════════════════════════════════════

    public static class ValidationResult {

        public enum Status { PASS, WARN, FAIL, SKIP }

        private final Status status;
        private final String message;

        private ValidationResult(Status status, String message) {
            this.status  = status;
            this.message = message;
        }

        public static ValidationResult pass(String message) { return new ValidationResult(Status.PASS, message); }
        public static ValidationResult warn(String message) { return new ValidationResult(Status.WARN, message); }
        public static ValidationResult fail(String message) { return new ValidationResult(Status.FAIL, message); }
        public static ValidationResult skip(String message) { return new ValidationResult(Status.SKIP, message); }

        /** True if PASS, WARN, or SKIP — only FAIL blocks the pipeline */
        public boolean isValid()   { return status != Status.FAIL; }
        public boolean isWarning() { return status == Status.WARN; }
        public Status  getStatus() { return status; }
        public String  getMessage(){ return message; }

        @Override
        public String toString() {
            return "[" + status + "] " + message;
        }
    }
}