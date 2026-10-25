package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.SupplierRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SupplierRequestDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private SupplierRequestDto dto(String email, String phone) {
        SupplierRequestDto dto = new SupplierRequestDto();
        dto.setName("Tigo");
        dto.setEmail(email);
        dto.setPhone(phone);
        return dto;
    }

    private Set<String> fieldsWithErrors(SupplierRequestDto dto) {
        return validator.validate(dto).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void validSupplierPasses() {
        assertTrue(fieldsWithErrors(dto("ventas@tigo.com", "+502 5555-1234")).isEmpty());
        assertTrue(fieldsWithErrors(dto("ventas@tigo.com", "(502) 55551234")).isEmpty());
    }

    @Test
    void emailAndPhoneAreRequired() {
        assertEquals(Set.of("email", "phone"), fieldsWithErrors(dto(null, null)));
        assertEquals(Set.of("email", "phone"), fieldsWithErrors(dto("  ", " ")));
    }

    @Test
    void rejectsMalformedEmailAndPhone() {
        assertEquals(Set.of("email"), fieldsWithErrors(dto("no-es-correo", "55551234")));
        assertEquals(Set.of("phone"), fieldsWithErrors(dto("a@b.com", "123")));
        assertEquals(Set.of("phone"), fieldsWithErrors(dto("a@b.com", "5555-abcd")));
    }
}
