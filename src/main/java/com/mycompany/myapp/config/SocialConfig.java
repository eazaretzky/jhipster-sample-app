package com.mycompany.myapp.config;

import javax.inject.Inject;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.social.UserIdSource;
import org.springframework.social.config.annotation.ConnectionFactoryConfigurer;
import org.springframework.social.config.annotation.EnableSocial;
import org.springframework.social.config.annotation.SocialConfigurerAdapter;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.ConnectionSignUp;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.social.connect.mem.InMemoryUsersConnectionRepository;
import org.springframework.social.facebook.connect.FacebookConnectionFactory;
import org.springframework.social.google.connect.GoogleConnectionFactory;

import com.mycompany.myapp.security.SecurityUtils;

@Configuration
@EnableSocial
public class SocialConfig extends SocialConfigurerAdapter {

    @Inject
    private ConnectionSignUp signup;

    @Override
    public void addConnectionFactories(ConnectionFactoryConfigurer connectionFactoryConfigurer,	Environment environment) {
        connectionFactoryConfigurer.addConnectionFactory(
                new GoogleConnectionFactory(
                        environment.getProperty("spring.social.google.clientId"),
                        environment.getProperty("spring.social.google.clientSecret")
                )
        );

        connectionFactoryConfigurer.addConnectionFactory(
                new FacebookConnectionFactory(
                        environment.getProperty("spring.social.facebook.clientId"),
                        environment.getProperty("spring.social.facebook.clientSecret")
                )
        );
    }

    @Override
    public UserIdSource getUserIdSource() {
        return new UserIdSource() {
            @Override
            public String getUserId() {
                return SecurityUtils.getCurrentLogin();
            }
        };
    }

    @Override
    public UsersConnectionRepository getUsersConnectionRepository(ConnectionFactoryLocator connectionFactoryLocator) {
        InMemoryUsersConnectionRepository repo = new InMemoryUsersConnectionRepository(connectionFactoryLocator);
        repo.setConnectionSignUp(signup);
        return repo;
    }



}
