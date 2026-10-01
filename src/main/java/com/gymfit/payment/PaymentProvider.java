package com.gymfit.payment;

public interface PaymentProvider {

    PaymentProviderCode providerCode();

    String createReference(Payment payment);
}