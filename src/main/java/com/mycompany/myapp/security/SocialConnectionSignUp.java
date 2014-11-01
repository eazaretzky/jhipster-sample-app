package com.mycompany.myapp.security;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionKey;
import org.springframework.social.connect.ConnectionSignUp;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.domain.ExternalAccountProvider;
import com.mycompany.myapp.repository.UserRepository;

@Component("socialConnectionSignUp")
public class SocialConnectionSignUp implements ConnectionSignUp {
    private final Logger log = LoggerFactory.getLogger(SocialConnectionSignUp.class);

    @Inject
    private UserRepository userRepository;

    @Transactional(readOnly = true)
    @Override
    public String execute(Connection<?> connection) {
        ConnectionKey key = connection.getKey();
        String providerName = key.getProviderId();
        ExternalAccountProvider externalProvider = ExternalAccountProvider.caseInsensitiveValueOf(providerName);
        String externalId = key.getProviderUserId();

        User user = userRepository.getUserByExternalAccount(externalProvider, externalId);
        if (user != null) {
            String internalLogin = user.getLogin();
            log.debug("Returning existing internal User '{}' for external login '{}' from {}", internalLogin, externalId, externalProvider);
            return internalLogin;
        }
        else {
            log.debug("No internal User for external login '{}' from {}", externalId, externalProvider);
            return null;
        }
    }


}
