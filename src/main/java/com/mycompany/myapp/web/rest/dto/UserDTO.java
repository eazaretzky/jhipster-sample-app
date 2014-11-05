package com.mycompany.myapp.web.rest.dto;

import com.mycompany.myapp.domain.ExternalAccount;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserDTO {

    private String login;

    private String password;

    private String firstName;

    private String lastName;

    private String email;

    private String langKey;

    private List<String> roles;

    private Set<ExternalAccount> externalAccounts = new HashSet<>();

    public UserDTO() {
    }

    public UserDTO(String login, String firstName, String lastName, String email, String langKey,
                   List<String> roles, Set<ExternalAccount> externalAccounts) {
        this.login = login;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.langKey = langKey;
        this.roles = roles;
        this.externalAccounts.addAll(externalAccounts);
    }

    public UserDTO(String firstName, String lastName, String email, ExternalAccount externalAccount) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.externalAccounts.add(externalAccount);
    }

    public String getPassword() {
        return password;
    }

    public String getLogin() {
        return login;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getLangKey() {
        return langKey;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Set<ExternalAccount> getExternalAccounts() {
        return Collections.unmodifiableSet(externalAccounts);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UserDTO{");
        sb.append("login='").append(login).append('\'');
        if(password != null) {
            sb.append(", password='").append(password.length()).append('\'');
        }
        sb.append(", firstName='").append(firstName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", email='").append(email).append('\'');
        sb.append(", langKey='").append(langKey).append('\'');
        sb.append(", roles=").append(roles);
        sb.append(", externalAccounts=" + externalAccounts);
        sb.append('}');
        return sb.toString();
    }
}
