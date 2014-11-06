package com.mycompany.myapp.config;

import com.mycompany.myapp.security.*;
import com.mycompany.myapp.security.social.SocialLoginExceptionMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.social.security.SocialAuthenticationException;
import org.springframework.social.security.SocialAuthenticationFilter;
import org.springframework.social.security.SpringSocialConfigurer;

import javax.inject.Inject;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Inject
    private Environment env;

    @Inject
    private AjaxAuthenticationSuccessHandler ajaxAuthenticationSuccessHandler;

    @Inject
    private AjaxAuthenticationFailureHandler ajaxAuthenticationFailureHandler;

    @Inject
    private AjaxLogoutSuccessHandler ajaxLogoutSuccessHandler;

    @Inject
    private Http401UnauthorizedEntryPoint authenticationEntryPoint;

    @Inject
    private UserDetailsService userDetailsService;

    @Inject
    private RememberMeServices rememberMeServices;

    @Bean
    public PasswordEncoder  passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Inject
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring()
                .antMatchers("/bower_components/**")
                .antMatchers("/fonts/**")
                .antMatchers("/images/**")
                .antMatchers("/scripts/**")
                .antMatchers("/styles/**")
                .antMatchers("/views/**")
                .antMatchers("/i18n/**")
                .antMatchers("/swagger-ui/**")
                .antMatchers("/console/**");
    }

    /**
     * Build a configurer that can be applied to an HttpSecurity instance.  When the configurer is applied,
     * Spring Social Security's {@link org.springframework.social.security.SocialAuthenticationFilter}
     * will be added to the HttpSecurity's SecurityFilterChain.
     * @return
     */
    protected SpringSocialConfigurer buildSpringSocialConfigurer() {
        // build an AuthenticationFailureHandler that is aware of our own exception types
        final SocialLoginExceptionMapper handler = new SocialLoginExceptionMapper("/#/register-external")
            .add(SocialAuthenticationException.class, "/#/register-external/rejected")
            .add(UserNotActivatedException.class, "/#/activate");

        SpringSocialConfigurer configurer = new SpringSocialConfigurer()
                .postLoginUrl("/")
                .alwaysUsePostLoginUrl(true);

        // configure options not available using the standard configurer
        configurer.addObjectPostProcessor(
            new ObjectPostProcessor<SocialAuthenticationFilter>() {
                public SocialAuthenticationFilter postProcess(SocialAuthenticationFilter object) {
                    // replace the default exception
                    object.setAuthenticationFailureHandler(handler);

                    object.setSignupUrl("/#/register-external");
                    return object;
                }
            }
        );

        return configurer;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint)
                .and()
                .rememberMe()
                .rememberMeServices(rememberMeServices)
                .key(env.getProperty("jhipster.security.rememberme.key"))
                .and()
                .formLogin()
                .loginProcessingUrl("/app/authentication")
                .successHandler(ajaxAuthenticationSuccessHandler)
                .failureHandler(ajaxAuthenticationFailureHandler)
                .usernameParameter("j_username")
                .passwordParameter("j_password")
                .permitAll()
                .and()
                .apply(buildSpringSocialConfigurer()).and()
                .logout()
                .logoutUrl("/app/logout")
                .logoutSuccessHandler(ajaxLogoutSuccessHandler)
                .deleteCookies("JSESSIONID")
                .permitAll()
                .and()
                .csrf()
                .disable()
                .headers()
                .frameOptions().disable()
                .authorizeRequests()
                .antMatchers("/app/rest/register").permitAll()
                .antMatchers("/app/rest/activate").permitAll()
                .antMatchers("/app/rest/authenticate").permitAll()
                .antMatchers("/auth/**").permitAll()
                .antMatchers("/app/rest/logs/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/app/**").authenticated()
                .antMatchers("/metrics/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/health/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/trace/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/dump/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/shutdown/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/beans/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/info/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/autoconfig/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/env/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/trace/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/api-docs/**").hasAuthority(AuthoritiesConstants.ADMIN)
                .antMatchers("/protected/**").authenticated();
    }



    @EnableGlobalMethodSecurity(prePostEnabled = true, jsr250Enabled = true)
    private static class GlobalSecurityConfiguration extends GlobalMethodSecurityConfiguration {
    }
}