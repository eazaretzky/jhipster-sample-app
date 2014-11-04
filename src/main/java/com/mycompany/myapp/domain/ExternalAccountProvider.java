package com.mycompany.myapp.domain;

import org.apache.commons.lang.StringUtils;

/**
 * Supported social providers.  Many other providers are supported, see http://projects.spring.io/spring-social/.
 */
public enum ExternalAccountProvider {
    FACEBOOK,
    GOOGLE,
    LINKEDIN,
    TWITTER;

    public static ExternalAccountProvider caseInsensitiveValueOf(String value) {
        if (StringUtils.isNotBlank(value))
            return ExternalAccountProvider.valueOf(value.toUpperCase());
        else
            return null;
    }
}
