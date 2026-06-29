package dev.waiz.datamanager.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * BusinessValidationService
 *
 * Cross-field business rules validated AFTER regex validation passes.
 * Based on TNB Sokongan Majikan letter structure.
 *
 * Rules implemented:
 *  1. Start date must be in the past
 *  2. Calculated service duration must match extracted [TEMPOH_BERKHIDMAT]
 *  3. Letter date must not be in the future (max today)
 *  4. End date (if present) must be after start date
 *  5. MyKad birth year must be consistent with service start date (age ≥ 18)
 *  6. Staff ID must be present if MyKad is present
 */
@Service
@Slf4j
public class BusinessValidationService {

    // ══════════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════════

    public List<BusinessViolation> validate(Map<String, String> mappedFields) {
        List<BusinessViolation> violations = new ArrayList<>();

        // Run all business rules
        checkLetterDateNotFuture(mappedFields, violations);
        checkStartDateInPast(mappedFields, violations);
        checkEndDateAfterStartDate(mappedFields, violations);
        checkServiceDurationConsistency(mappedFields, violations);
        checkMinimumAgeAtServiceStart(mappedFields, violations);
        checkStaffIdPresentWithMyKad(mappedFields, violations);

        if (violations.isEmpty()) {
            log.info("Business validation passed — all rules satisfied");
        } else {
            violations.forEach(v ->
                log.warn("Business rule violation [{}]: {}", v.getRule(), v.getMessage()));
        }

        return violations;
    }

    public boolean hasCriticalViolations(List<BusinessViolation> violations) {
        return violations.stream().anyMatch(v -> v.getSeverity() == BusinessViolation.Severity.ERROR);
    }

    public List<BusinessViolation> getErrors(List<BusinessViolation> violations) {
        return violations.stream()
            .filter(v -> v.getSeverity() == BusinessViolation.Severity.ERROR)
            .toList();
    }

    public List<BusinessViolation> getWarnings(List<BusinessViolation> violations) {
        return violations.stream()
            .filter(v -> v.getSeverity() == BusinessViolation.Severity.WARNING)
            .toList();
    }

    // ══════════════════════════════════════════════════════════════
    //  Rule 1 — Letter date must not be in the future
    // ══════════════════════════════════════════════════════════════

    private void checkLetterDateNotFuture(Map<String, String> fields,
                                           List<BusinessViolation> violations) {
        String tarikh = getField(fields, "[TARIKH]", "[TARIKH_SURAT]");
        if (tarikh == null) return;

        LocalDate letterDate = parseDate(tarikh);
        if (letterDate == null) return;

        if (letterDate.isAfter(LocalDate.now())) {
            violations.add(BusinessViolation.error(
                "LETTER_DATE_FUTURE",
                "Letter date '" + tarikh + "' is in the future. " +
                "Letter date must be today or earlier."));
        } else {
            log.info("Rule 1 passed — letter date '{}' is not in the future", tarikh);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Rule 2 — Service start date must be in the past
    // ══════════════════════════════════════════════════════════════

    private void checkStartDateInPast(Map<String, String> fields,
                                       List<BusinessViolation> violations) {
        String startDate = getField(fields,
            "[TARIKH_MULA_BERKHIDMAT]", "[TARIKH_MULA]");
        if (startDate == null) return;

        LocalDate start = parseDate(startDate);
        if (start == null) return;

        if (!start.isBefore(LocalDate.now())) {
            violations.add(BusinessViolation.error(
                "START_DATE_NOT_IN_PAST",
                "Service start date '" + startDate + "' must be in the past."));
        } else {
            log.info("Rule 2 passed — start date '{}' is in the past", startDate);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Rule 3 — End date must be after start date (if both present)
    // ══════════════════════════════════════════════════════════════

    private void checkEndDateAfterStartDate(Map<String, String> fields,
                                             List<BusinessViolation> violations) {
        String startRaw = getField(fields, "[TARIKH_MULA]", "[TARIKH_MULA_BERKHIDMAT]");
        String endRaw   = getField(fields, "[TARIKH_TAMAT]", "[TARIKH_AKHIR]");

        if (startRaw == null || endRaw == null) return;

        LocalDate start = parseDate(startRaw);
        LocalDate end   = parseDate(endRaw);
        if (start == null || end == null) return;

        if (!end.isAfter(start)) {
            violations.add(BusinessViolation.error(
                "END_DATE_BEFORE_START_DATE",
                "End date '" + endRaw + "' must be after start date '" + startRaw + "'."));
        } else {
            log.info("Rule 3 passed — end date '{}' is after start date '{}'", endRaw, startRaw);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Rule 4 — Service duration must match calculated years
    //  e.g. start=30/5/2014, letter date=24/7/2025 → 11 tahun ✅
    // ══════════════════════════════════════════════════════════════

    private void checkServiceDurationConsistency(Map<String, String> fields,
                                                  List<BusinessViolation> violations) {
        String startRaw  = getField(fields, "[TARIKH_MULA_BERKHIDMAT]", "[TARIKH_MULA]");
        String tempoh    = getField(fields, "[TEMPOH_BERKHIDMAT]", "[TEMPOH]");
        String tarikhRaw = getField(fields, "[TARIKH]", "[TARIKH_SURAT]");

        if (startRaw == null || tempoh == null) return;

        LocalDate start      = parseDate(startRaw);
        LocalDate referenceDate = tarikhRaw != null ? parseDate(tarikhRaw) : LocalDate.now();
        if (start == null || referenceDate == null) return;

        // Extract stated years from tempoh e.g. "11 tahun" → 11
        int statedYears = extractYears(tempoh);
        if (statedYears < 0) {
            log.warn("Could not parse years from tempoh: '{}'", tempoh);
            return;
        }

        // Calculate actual years
        int actualYears = Period.between(start, referenceDate).getYears();

        // Allow ±1 year tolerance (document may be written mid-year)
        if (Math.abs(actualYears - statedYears) > 1) {
            violations.add(BusinessViolation.warning(
                "SERVICE_DURATION_MISMATCH",
                "Stated service duration '" + tempoh + "' (" + statedYears + " years) " +
                "does not match calculated duration from '" + startRaw +
                "' to '" + (tarikhRaw != null ? tarikhRaw : "today") +
                "' (" + actualYears + " years). Please verify."));
        } else {
            log.info("Rule 4 passed — service duration '{}' matches calculated {} years",
                tempoh, actualYears);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Rule 5 — Staff must be at least 18 at service start date
    //  Derived from MyKad birth year (first 6 digits: YYMMDD)
    // ══════════════════════════════════════════════════════════════

    private void checkMinimumAgeAtServiceStart(Map<String, String> fields,
                                                List<BusinessViolation> violations) {
        String noKp      = getField(fields, "[NO_KP]", "[NO_PENGENALAN]", "[NO_K_PENGENALAN]");
        String startRaw  = getField(fields, "[TARIKH_MULA_BERKHIDMAT]", "[TARIKH_MULA]");

        if (noKp == null || startRaw == null) return;

        // Parse birth date from MyKad
        String normalized = noKp.replace("-", "").replace(" ", "");
        if (normalized.length() < 6) return;

        try {
            int yy    = Integer.parseInt(normalized.substring(0, 2));
            int mm    = Integer.parseInt(normalized.substring(2, 4));
            int dd    = Integer.parseInt(normalized.substring(4, 6));

            // Determine century: if YY > current year's last 2 digits → 1900s
            int currentYY = LocalDate.now().getYear() % 100;
            int fullYear  = (yy > currentYY) ? 1900 + yy : 2000 + yy;

            LocalDate birthDate  = LocalDate.of(fullYear, mm, dd);
            LocalDate startDate  = parseDate(startRaw);
            if (startDate == null) return;

            int ageAtStart = Period.between(birthDate, startDate).getYears();

            if (ageAtStart < 18) {
                violations.add(BusinessViolation.error(
                    "UNDERAGE_AT_SERVICE_START",
                    "Staff age at service start date '" + startRaw + "' is " + ageAtStart +
                    " years (based on MyKad '" + noKp + "'). Minimum age is 18."));
            } else {
                log.info("Rule 5 passed — staff was {} years old at service start", ageAtStart);
            }

        } catch (NumberFormatException | java.time.DateTimeException e) {
            log.warn("Could not derive birth date from MyKad '{}': {}", noKp, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Rule 6 — Staff ID must be present if MyKad is provided
    // ══════════════════════════════════════════════════════════════

    private void checkStaffIdPresentWithMyKad(Map<String, String> fields,
                                               List<BusinessViolation> violations) {
        String noKp      = getField(fields, "[NO_KP]", "[NO_PENGENALAN]");
        String noPerkerja = getField(fields, "[NO_PEKERJA]", "[NO_KAKITANGAN]");

        if (noKp != null && (noPerkerja == null || noPerkerja.isBlank())) {
            violations.add(BusinessViolation.error(
                "MISSING_STAFF_ID",
                "MyKad number '" + noKp + "' is present but Staff ID [NO_PEKERJA] is missing. " +
                "Both are required for TNB Sokongan Majikan letters."));
        } else if (noKp != null) {
            log.info("Rule 6 passed — both MyKad '{}' and Staff ID '{}' are present",
                noKp, noPerkerja);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the first non-null, non-blank value from the given placeholders.
     */
    private String getField(Map<String, String> fields, String... placeholders) {
        for (String ph : placeholders) {
            String val = fields.get(ph);
            if (val != null && !val.isBlank()) return val.trim();
        }
        return null;
    }

    /**
     * Attempts to parse a date string in multiple formats.
     * Supports: dd/MM/yyyy, d/M/yyyy, dd-MM-yyyy, yyyy-MM-dd, "24 Julai 2025"
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;

        // Malay month name map
        String normalized = value.trim()
            .replace("Januari",   "01").replace("Februari",  "02")
            .replace("Mac",       "03").replace("April",     "04")
            .replace("Mei",       "05").replace("Jun",       "06")
            .replace("Julai",     "07").replace("Ogos",      "08")
            .replace("September", "09").replace("Oktober",   "10")
            .replace("November",  "11").replace("Disember",  "12");

        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("d MM yyyy"),
            DateTimeFormatter.ofPattern("dd MM yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("dd/MM/yy")
        );

        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(normalized, fmt);
            } catch (DateTimeParseException ignored) {}
        }

        log.warn("Could not parse date: '{}'", value);
        return null;
    }

    /**
     * Extracts numeric year count from "11 tahun", "2 years", "11 tahun 3 bulan"
     */
    private int extractYears(String tempoh) {
        if (tempoh == null) return -1;
        try {
            String[] parts = tempoh.trim().split("\\s+");
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  BusinessViolation model
    // ══════════════════════════════════════════════════════════════

    public static class BusinessViolation {

        public enum Severity { ERROR, WARNING }

        private final String   rule;
        private final String   message;
        private final Severity severity;

        private BusinessViolation(String rule, String message, Severity severity) {
            this.rule     = rule;
            this.message  = message;
            this.severity = severity;
        }

        public static BusinessViolation error(String rule, String message) {
            return new BusinessViolation(rule, message, Severity.ERROR);
        }

        public static BusinessViolation warning(String rule, String message) {
            return new BusinessViolation(rule, message, Severity.WARNING);
        }

        public String   getRule()     { return rule; }
        public String   getMessage()  { return message; }
        public Severity getSeverity() { return severity; }

        @Override
        public String toString() {
            return "[" + severity + "] " + rule + ": " + message;
        }
    }
}