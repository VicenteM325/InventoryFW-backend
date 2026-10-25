package com.vm325.inventory_back.controllers;

import com.vm325.inventory_back.dtos.NotificationResponseDto;
import com.vm325.inventory_back.enums.RoleList;
import com.vm325.inventory_back.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponseDto>> findAll(
            @RequestParam(required = false) Boolean read, Authentication authentication) {
        return ResponseEntity.ok(notificationService.findAll(read, roleOf(authentication)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDto> markAsRead(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAsRead(id, roleOf(authentication)));
    }

    private RoleList roleOf(Authentication authentication) {
        // @PreAuthorize ya garantiza que el usuario tiene ROLE_ADMIN o ROLE_EMPLOYEE.
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(RoleList::valueOf)
                .findFirst()
                .orElseThrow();
    }
}
