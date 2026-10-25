package com.vm325.inventory_back.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import com.vm325.inventory_back.entities.User;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class UserResponseDto {
    private UUID userId;
    private String name;
    private String userName;
    private String email;
    private boolean twoFactorEnabled;
    private String roleName;
    private boolean active;

    public static UserResponseDto from(User user) {
        return UserResponseDto.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .userName(user.getUserName())
                .email(user.getEmail())
                .twoFactorEnabled(user.isTwoFactorEnabled())
                .roleName(user.getRole().getName().toString())
                .active(user.isActive())
                .build();
    }
}
