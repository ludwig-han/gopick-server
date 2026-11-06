package com.gopick.server.account.service;

import com.gopick.server.account.domain.Account;
import com.gopick.server.account.dto.CurrentUserResponse;
import com.gopick.server.account.exception.InvalidCredentialsException;
import com.gopick.server.account.repository.AccountRepository;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.repository.ProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AccountRepository accountRepository, ProfileRepository profileRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }


    public CurrentUserResponse login(String email, String password) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, account.getPassword())) {
            throw new InvalidCredentialsException();
        }

        Long profileId = profileRepository.findByAccount_Id(account.getId())
                .map(Profile::getId)
                .orElse(null);
        return new CurrentUserResponse(account.getId(), account.getEmail(), profileId);
    }
}
