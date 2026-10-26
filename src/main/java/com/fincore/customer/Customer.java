package com.fincore.customer;

import com.fincore.common.Identifiable;

import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A bank customer (account holder).
 * <p>
 * Identity fields are {@code final}; only contact details can change, and only through
 * {@link #updateContact(String, String)}, which re-validates them. There are no setters.
 */
public final class Customer implements Identifiable<String> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{9,15}$");

    private final String id;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final LocalDate registeredOn;
    private String email;
    private String phone;

    public Customer(String id, String fullName, LocalDate dateOfBirth, String email, String phone,
                    LocalDate registeredOn) {
        this.id = requireText(id, "id");
        this.fullName = requireText(fullName, "fullName").trim();
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        this.registeredOn = Objects.requireNonNull(registeredOn, "registeredOn must not be null");
        if (dateOfBirth.isAfter(registeredOn)) {
            throw new IllegalArgumentException("Date of birth cannot be in the future");
        }
        this.email = validateEmail(email);
        this.phone = validatePhone(phone);
    }

    public void updateContact(String newEmail, String newPhone) {
        // Validate both before changing anything, so the object never ends up half-updated.
        String validEmail = validateEmail(newEmail);
        String validPhone = validatePhone(newPhone);
        this.email = validEmail;
        this.phone = validPhone;
    }

    public int ageOn(LocalDate date) {
        return ageBetween(dateOfBirth, date);
    }

    /** Completed years between birth and the given date (static: usable before a Customer exists). */
    public static int ageBetween(LocalDate dateOfBirth, LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }

    private static String validateEmail(String value) {
        String normalized = requireText(value, "email").trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
        return normalized;
    }

    private static String validatePhone(String value) {
        String normalized = requireText(value, "phone").replaceAll("[\\s.-]", "");
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid phone number: " + value);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public LocalDate getRegisteredOn() {
        return registeredOn;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    /** Entities are equal when their identities are equal. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Customer other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Customer[id=%s, name=%s]".formatted(id, fullName);
    }
}
