package com.gopick.server.account.service;

import com.gopick.server.account.domain.Account;
import com.gopick.server.account.dto.CurrentUserResponse;
import com.gopick.server.account.exception.InvalidCredentialsException;
import com.gopick.server.account.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AccountRepository accountRepository,  PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }


    public CurrentUserResponse login(String email, String password) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return new CurrentUserResponse(account.getId(), account.getEmail(), null);  // todo: profile 구현 후 accountId로 profileId 조회
    }
}
