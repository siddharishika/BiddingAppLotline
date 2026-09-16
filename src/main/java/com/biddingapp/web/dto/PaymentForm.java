package com.biddingapp.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PaymentForm {

    @NotBlank
    @Size(min = 2, max = 80)
    private String cardholderName;

    @NotBlank
    @Pattern(regexp = "^[0-9]{13,19}$", message = "Enter a valid card number")
    private String cardNumber;

    @NotBlank
    @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Use MM/YY")
    private String expiry;

    @NotBlank
    @Pattern(regexp = "^[0-9]{3,4}$", message = "Enter a valid CVC")
    private String cvc;

    public String getCardholderName() {
        return cardholderName;
    }

    public void setCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber == null ? null : cardNumber.replaceAll("\\s", "");
    }

    public String getExpiry() {
        return expiry;
    }

    public void setExpiry(String expiry) {
        this.expiry = expiry;
    }

    public String getCvc() {
        return cvc;
    }

    public void setCvc(String cvc) {
        this.cvc = cvc;
    }
}
