package com.gopick.server.account.service;

import com.gopick.server.account.domain.Account;
import com.gopick.server.account.dto.CreateAccountResponse;
import com.gopick.server.account.exception.DuplicateEmailException;
import com.gopick.server.account.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository,  PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public CreateAccountResponse createAccount(String email, String password) {
        // 이미 가입한 경우 처리
        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        String encodedPassword = passwordEncoder.encode(password);
        Account account = new Account(email, encodedPassword);
        Account result = accountRepository.save(account);
        return new CreateAccountResponse(result.getId(), result.getEmail(), result.getCreatedAt());
    }
}
