package com.eczam.scheduler;

import com.eczam.auth.token.EmailVerificationTokenRepository;
import com.eczam.auth.token.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Periodically removes expired tokens so the auth tables don't grow unbounded.
 * Runs at 03:00 server time every day (low-traffic window).
 */
@Slf4j
@Component
public class TokenCleanupTask {

    private final RefreshTokenRepository refreshTokens;
    private final EmailVerificationTokenRepository verificationTokens;

    public TokenCleanupTask(RefreshTokenRepository refreshTokens,
                            EmailVerificationTokenRepository verificationTokens) {
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
    }

    @Scheduled(cron = "0 0 3 * * *")  // 03:00 every day
    @Transactional
    public void cleanupExpiredTokens() {
        OffsetDateTime cutoff = OffsetDateTime.now();

        int deletedRefresh = refreshTokens.deleteExpiredBefore(cutoff);
        int deletedVerify  = verificationTokens.deleteExpiredBefore(cutoff);

        log.info("Token cleanup: removed {} expired refresh tokens, {} verification tokens",
                deletedRefresh, deletedVerify);
    }
}
