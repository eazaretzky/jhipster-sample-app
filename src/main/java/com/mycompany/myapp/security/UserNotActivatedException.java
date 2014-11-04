package com.mycompany.myapp.security;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * This exception is throw in case of a not activated user trying to authenticate.
 */
public class UserNotActivatedException extends UsernameNotFoundException {

    public UserNotActivatedException(String message) {
        super(message);
    }

    public UserNotActivatedException(String message, Throwable t) {
        super(message, t);
    }
}
