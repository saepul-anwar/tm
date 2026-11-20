package com.studenttask.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import com.studenttask.auth.dto.LoginRequest;
import com.studenttask.auth.dto.RegisterRequest;
import com.studenttask.auth.service.AuthService;
import com.studenttask.common.error.AppException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/** The slow BCrypt check must not hold a database connection inside a transaction. */
class LoginTransactionIT extends MySqlTestBase {

    private static final String PASSWORD = "Passw0rd-Synthetic1";

    @Autowired
    AuthService authService;

    @MockitoSpyBean
    PasswordEncoder passwordEncoder;

    private List<Boolean> recordMatchesTransactionState() {
        List<Boolean> states = new ArrayList<>();
        reset(passwordEncoder);
        doAnswer(inv -> {
            states.add(TransactionSynchronizationManager.isActualTransactionActive());
            return inv.callRealMethod();
        }).when(passwordEncoder).matches(anyString(), anyString());
        return states;
    }

    @Test
    void password_check_runs_outside_any_transaction_for_known_and_unknown_email() {
        String email = "user-" + UUID.randomUUID() + "@example.test";
        authService.register(new RegisterRequest("Budi Santoso", email, PASSWORD, PASSWORD));
        List<Boolean> states = recordMatchesTransactionState();

        authService.login(new LoginRequest(email, PASSWORD));
        try {
            authService.login(new LoginRequest("nobody-" + UUID.randomUUID() + "@example.test", PASSWORD));
        } catch (AppException expected) {
            // generic 401 for unknown email
        }

        assertThat(states).hasSize(2).containsOnly(false);
    }
}
