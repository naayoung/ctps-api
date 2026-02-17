package com.ctps.ctps_api.domain.auth.controller;

import com.ctps.ctps_api.domain.auth.dto.AuthRequest;
import com.ctps.ctps_api.domain.auth.service.AuthService;
import com.ctps.ctps_api.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@RequestBody AuthRequest request) {
        authService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success("로그인 성공"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh() {
        authService.refresh();
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공"));
    }
}
