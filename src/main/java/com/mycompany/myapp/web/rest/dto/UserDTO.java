package com.mycompany.myapp.web.rest.dto;

import com.mycompany.myapp.domain.ExternalAccountProvider;

import java.util.List;


public class UserDTO {

    private String login;

    private String password;

    private String firstName;

    private String lastName;

    private String email;

    private String langKey;

    private List<String> roles;

    private ExternalAccountProvider externalAccountProvider;

    private String externalId;

    public UserDTO() {
    }

    public UserDTO(String login, String password, String firstName, String lastName, String email, String langKey,
                   List<String> roles, ExternalAccountProvider externalAccountProvider, String externalId) {
        this.login = login;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.langKey = langKey;
        this.roles = roles;
        this.externalAccountProvider = externalAccountProvider;
        this.externalId = externalId;
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

    public ExternalAccountProvider getExternalAccountProvider() {
        return externalAccountProvider;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
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
        sb.append(", roles='").append(roles).append('\'');
        sb.append(", externalAccountProvider='").append(externalAccountProvider).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
