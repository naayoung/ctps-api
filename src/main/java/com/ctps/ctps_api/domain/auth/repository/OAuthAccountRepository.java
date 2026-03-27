package com.ctps.ctps_api.domain.auth.repository;

import com.ctps.ctps_api.domain.auth.entity.OAuthAccount;
import com.ctps.ctps_api.domain.auth.entity.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
