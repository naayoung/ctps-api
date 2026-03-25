package com.ctps.ctps_api.global.config;

import com.ctps.ctps_api.global.response.ApiResponse;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/", "/health", "/api/health"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success("헬스 체크 성공", Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        )));
    }
}
