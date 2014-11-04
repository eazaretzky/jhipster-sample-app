package com.mycompany.myapp.security.social;

import com.mycompany.myapp.security.SecurityUtils;
import org.springframework.social.UserIdSource;


/**
 * A UserIdSource that delegates to {@link com.mycompany.myapp.security.SecurityUtils#getCurrentLogin()}.
 */
public class SecurityUtilsUserIdSource implements UserIdSource {
    @Override
    public String getUserId() {
        return SecurityUtils.getCurrentLogin();
    }
}
