package com.gopick.server.account.service;

import com.gopick.server.account.domain.Account;
import com.gopick.server.account.dto.CreateAccountResponse;
import com.gopick.server.account.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository,  PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public CreateAccountResponse createAccount(String email, String password) {
        String encodedPassword = passwordEncoder.encode(password);
        Account account = new Account(email, encodedPassword);
        Account result = accountRepository.save(account);
        return new CreateAccountResponse(result.getId(), result.getEmail(), result.getCreatedAt());
    }
}
