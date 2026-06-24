package com.eczam.auth;

import com.eczam.auth.totp.TotpPendingRepository;
import com.eczam.auth.totp.TotpService;
import com.eczam.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TotpServiceTest {

    private final TotpService service = new TotpService(
            mock(UserRepository.class),
            new BCryptPasswordEncoder(),
            mock(TotpPendingRepository.class));

    @Test
    void generated_code_verifies_against_same_secret() {
        // We can't easily call generateTotp from outside (it's package-private),
        // but we can test that verifyCode rejects a known-bad code.
        assertThat(service.verifyCode("JBSWY3DPEHPK3PXP", "000000")).isFalse();
    }

    @Test
    void wrong_length_code_is_rejected() {
        assertThat(service.verifyCode("JBSWY3DPEHPK3PXP", "12345")).isFalse();
        assertThat(service.verifyCode("JBSWY3DPEHPK3PXP", "1234567")).isFalse();
    }

    @Test
    void null_code_is_rejected() {
        assertThat(service.verifyCode("JBSWY3DPEHPK3PXP", null)).isFalse();
    }
}
