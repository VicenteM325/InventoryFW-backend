package com.vm325.inventory_back.controllers;

import com.vm325.inventory_back.dtos.ApiMessage;
import com.vm325.inventory_back.dtos.CodeDto;
import com.vm325.inventory_back.dtos.PasswordConfirmDto;
import com.vm325.inventory_back.dtos.UpdateEmailDto;
import com.vm325.inventory_back.dtos.UserResponseDto;
import com.vm325.inventory_back.services.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Autoservicio de la cuenta propia (cualquier usuario autenticado). Separado
// de UserController, que es exclusivo del rol ADMIN.
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PutMapping("/email")
    public ResponseEntity<UserResponseDto> updateEmail(@Valid @RequestBody UpdateEmailDto dto) {
        return ResponseEntity.ok(accountService.updateEmail(dto.getEmail(), dto.getCurrentPassword()));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<ApiMessage> enableTwoFactor() {
        accountService.requestEnableTwoFactor();
        return ResponseEntity.ok(new ApiMessage("Se envió un código de confirmación a su correo"));
    }

    @PostMapping("/2fa/confirm")
    public ResponseEntity<UserResponseDto> confirmTwoFactor(@Valid @RequestBody CodeDto dto) {
        return ResponseEntity.ok(accountService.confirmEnableTwoFactor(dto.getCode()));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<UserResponseDto> disableTwoFactor(@Valid @RequestBody PasswordConfirmDto dto) {
        return ResponseEntity.ok(accountService.disableTwoFactor(dto.getCurrentPassword()));
    }
}
