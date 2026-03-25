package com.ctps.ctps_api.domain.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ctps.ctps_api.domain.auth.entity.User;
import com.ctps.ctps_api.domain.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthBootstrapServiceTest {

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);

    private AuthBootstrapService createService(String mode, String railwayEnvironment, String vercelEnvironment, boolean enabled) {
        AuthBootstrapService service = new AuthBootstrapService(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(service, "bootstrapUsername", "ctps");
        ReflectionTestUtils.setField(service, "bootstrapPassword", "ctps1234");
        ReflectionTestUtils.setField(service, "bootstrapDisplayName", "CTPS 사용자");
        ReflectionTestUtils.setField(service, "bootstrapEnabled", enabled);
        ReflectionTestUtils.setField(service, "deploymentMode", mode);
        ReflectionTestUtils.setField(service, "railwayEnvironment", railwayEnvironment);
        ReflectionTestUtils.setField(service, "vercelEnvironment", vercelEnvironment);
        return service;
    }

    @Test
    @DisplayName("local 환경에서는 bootstrap 사용자를 생성한다")
    void ensureBootstrapUser_createsUserInLocal() {
        AuthBootstrapService service = createService("local", "", "", true);
        given(userRepository.findByUsername("ctps")).willReturn(Optional.empty());
        given(passwordEncoder.encode("ctps1234")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.ensureBootstrapUser();

        verify(userRepository).findByUsername("ctps");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("production 환경에서는 bootstrap 설정이 켜져 있어도 사용자를 생성하지 않는다")
    void ensureBootstrapUser_skipsInProduction() {
        AuthBootstrapService service = createService("production", "", "", true);

        service.ensureBootstrapUser();

        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Railway 환경이 감지되면 deployment mode와 무관하게 사용자를 생성하지 않는다")
    void ensureBootstrapUser_skipsInRailway() {
        AuthBootstrapService service = createService("local", "production", "", true);

        service.ensureBootstrapUser();

        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("bootstrap이 비활성화되면 local 환경에서도 생성하지 않는다")
    void ensureBootstrapUser_skipsWhenDisabled() {
        AuthBootstrapService service = createService("local", "", "", false);

        service.ensureBootstrapUser();

        verify(userRepository, never()).findByUsername(eq("ctps"));
        verify(userRepository, never()).save(any());
    }
}
