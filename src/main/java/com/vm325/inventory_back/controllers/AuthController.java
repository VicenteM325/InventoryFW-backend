package com.vm325.inventory_back.controllers;

import com.vm325.inventory_back.dtos.ApiMessage;
import com.vm325.inventory_back.dtos.LoginResponseDto;
import com.vm325.inventory_back.dtos.LoginUserDto;
import com.vm325.inventory_back.dtos.TwoFactorResendDto;
import com.vm325.inventory_back.dtos.TwoFactorVerifyDto;
import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.services.AuthService;
import com.vm325.inventory_back.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Autowired
    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginUserDto loginUserDto, BindingResult bindingResult, HttpServletResponse response){
        if (bindingResult.hasErrors()){
            String message = bindingResult.getFieldError() != null
                    ? bindingResult.getFieldError().getDefaultMessage()
                    : "Revise sus credenciales";
            return ResponseEntity.badRequest().body(new ApiMessage(message));
        }
        try {
            LoginResponseDto result = authService.login(loginUserDto.getUserName(), loginUserDto.getPassword(), response);
            return ResponseEntity.ok(result);
        } catch (AuthenticationException e) {
            // Credenciales erróneas, cuenta desactivada, etc.: mismo mensaje genérico
            // para no revelar si el usuario existe o su estado.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiMessage("Usuario o contraseña incorrectos"));
        }
    }


    @PostMapping("/2fa/verify")
    public ResponseEntity<LoginResponseDto> verifyTwoFactor(@Valid @RequestBody TwoFactorVerifyDto dto, HttpServletResponse response) {
        return ResponseEntity.ok(authService.verifyTwoFactor(dto.getChallengeToken(), dto.getCode(), response));
    }

    @PostMapping("/2fa/resend")
    public ResponseEntity<ApiMessage> resendTwoFactor(@Valid @RequestBody TwoFactorResendDto dto) {
        authService.resendTwoFactor(dto.getChallengeToken());
        return ResponseEntity.ok(new ApiMessage("Se envió un nuevo código a su correo"));
    }

    @GetMapping("/check-auth")
    public ResponseEntity<String> checkAuth(){
        return ResponseEntity.ok().body("Autenticado");
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiMessage> logout(HttpServletResponse response){
        authService.logout(response);
        return ResponseEntity.ok(new ApiMessage("Sesión cerrada"));
    }

    @GetMapping("/user/details")
    public ResponseEntity<User> getAuthenticateUser(){
        User user = userService.getUserDetails();
        return ResponseEntity.ok(user);
    }
}