package com.fincore.customer;

import com.fincore.common.BusinessCalendar;
import com.fincore.common.IdGenerator;
import com.fincore.exception.BusinessRuleException;
import com.fincore.exception.CustomerNotFoundException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Customer onboarding (simplified KYC) and lookup. */
public class CustomerService {

    /** Legal age required to open an account on one's own. */
    public static final int MINIMUM_AGE = 18;

    private final CustomerRepository customers;
    private final IdGenerator ids;
    private final BusinessCalendar calendar;

    public CustomerService(CustomerRepository customers, IdGenerator ids, BusinessCalendar calendar) {
        this.customers = customers;
        this.ids = ids;
        this.calendar = calendar;
    }

    public Customer register(String fullName, LocalDate dateOfBirth, String email, String phone)
            throws BusinessRuleException {
        LocalDate today = calendar.today();
        if (Customer.ageBetween(dateOfBirth, today) < MINIMUM_AGE) {
            throw new BusinessRuleException("Customer must be at least %d years old".formatted(MINIMUM_AGE));
        }
        if (customers.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("Email is already registered: " + email);
        }
        Customer customer = new Customer(ids.nextCustomerId(), fullName, dateOfBirth, email, phone, today);
        return customers.save(customer);
    }

    public Customer getById(String customerId) throws CustomerNotFoundException {
        return customers.findById(customerId.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    public Customer updateContact(String customerId, String email, String phone)
            throws CustomerNotFoundException, BusinessRuleException {
        Customer customer = getById(customerId);
        boolean emailTakenByOther = customers.findByEmail(email)
                .filter(other -> !other.equals(customer))
                .isPresent();
        if (emailTakenByOther) {
            throw new BusinessRuleException("Email is already registered: " + email);
        }
        customer.updateContact(email, phone);
        return customer;
    }

    public List<Customer> listAll() {
        return customers.findAll().stream()
                .sorted(Comparator.comparing(Customer::getId))
                .toList();
    }
}
