package com.mycompany.myapp.domain;

import org.apache.commons.lang.StringUtils;

public enum ExternalAccountProvider {
    FACEBOOK,
    GOOGLE;

    public static ExternalAccountProvider caseInsensitiveValueOf(String value) {
        if (StringUtils.isNotBlank(value))
            return ExternalAccountProvider.valueOf(value.toUpperCase());
        else
            return null;
    }
}
