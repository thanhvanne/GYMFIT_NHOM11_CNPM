package com.gymfit.payment;

import com.gymfit.common.util.CodeGenerator;
import org.springframework.stereotype.Component;

@Component
public class MomoPaymentProvider implements PaymentProvider {

    @Override
    public PaymentProviderCode providerCode() {
        return PaymentProviderCode.MOMO_SIMULATOR;
    }

    @Override
    public String createReference(Payment payment) {
        return CodeGenerator.generate("MOMO");
    }
}