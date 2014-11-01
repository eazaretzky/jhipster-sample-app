package com.mycompany.myapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.mycompany.myapp.domain.Authority;
import com.mycompany.myapp.domain.ExternalAccount;
import com.mycompany.myapp.domain.ExternalAccountProvider;
import com.mycompany.myapp.domain.PersistentToken;
import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.repository.PersistentTokenRepository;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.security.SecurityUtils;
import com.mycompany.myapp.service.MailService;
import com.mycompany.myapp.service.UserService;
import com.mycompany.myapp.web.rest.dto.UserDTO;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.UserProfile;
import org.springframework.social.connect.web.ProviderSignInAttempt;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.spring4.context.SpringWebContext;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/app")
public class AccountResource {

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    @Inject
    private ServletContext servletContext;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SpringTemplateEngine templateEngine;

    @Inject
    private UserRepository userRepository;

    @Inject
    private UserService userService;

    @Inject
    private PersistentTokenRepository persistentTokenRepository;

    @Inject
    private MailService mailService;

    UserDTO providerSignInAttemptAsUserDTO(HttpServletRequest request)
    {
        // FIXME: cache this rather than hit the remote service each time
        HttpSession session = request.getSession(false);
        if (session != null) {
            ProviderSignInAttempt attempt = (ProviderSignInAttempt) session.getAttribute(ProviderSignInAttempt.SESSION_ATTRIBUTE);

            if (attempt != null) {
                // FIXME: test the connection, it could be expired.
                Connection<?> connection = attempt.getConnection();
                UserProfile profile = connection.fetchUserProfile();

                String firstName = profile.getFirstName();
                String lastName = profile.getLastName();
                String email = profile.getEmail();

                String externalAccountProviderName = connection.getKey().getProviderId();
                ExternalAccountProvider externalAccountProvider = ExternalAccountProvider.caseInsensitiveValueOf(externalAccountProviderName);

                return new UserDTO(null, null, firstName, lastName, email, "en_US", null, externalAccountProvider, connection.getKey().getProviderUserId());
            }
        }
        return null;
    }

    @RequestMapping(value = "/rest/register",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<?> getRegisterAccount(HttpServletRequest request) {
        UserDTO dto = providerSignInAttemptAsUserDTO(request);

        if (dto != null)
            return new ResponseEntity<>(dto, HttpStatus.OK);
        else
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * POST  /rest/register -> register the user.
     */
    @RequestMapping(value = "/rest/register",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<?> registerAccount(@RequestBody UserDTO userDTO, HttpServletRequest request,
                                             HttpServletResponse response) {

        User user = null;
        if (userDTO.getLogin() != null)
            user = userRepository.findOne(userDTO.getLogin());

        if (user != null) {
            return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
        }
        else {

            String password = null;
            String email = null;
            ExternalAccount externalAccount = null;
            UserDTO externalAttempt = providerSignInAttemptAsUserDTO(request);

            if (externalAttempt == null) {
                // FIXME: no validation?  it appears that everything is done on the front end
                password = userDTO.getPassword();
                email = userDTO.getEmail();
            }
            else {
                log.debug("creating user from previous external authentication");

                if (StringUtils.isNotBlank(userDTO.getPassword()))
                    return new ResponseEntity<>("cannot specify a password for an account that is linked to an external provider", HttpStatus.BAD_REQUEST);

                externalAccount = new ExternalAccount();
                externalAccount.setExternalProvider(externalAttempt.getExternalAccountProvider());
                externalAccount.setExternalId(externalAttempt.getExternalId());

                if (StringUtils.isNotBlank(userDTO.getEmail()))
                    email = userDTO.getEmail();
                else
                    email = externalAttempt.getEmail();
            }
            user = userService.createUserInformation(userDTO.getLogin(), password, userDTO.getFirstName(),
                    userDTO.getLastName(), email.toLowerCase(), userDTO.getLangKey(), externalAccount);

            final Locale locale = Locale.forLanguageTag(user.getLangKey());
            String content = createHtmlContentFromTemplate(user, locale, request, response);
            mailService.sendActivationEmail(user.getEmail(), content, locale);
            return new ResponseEntity<>(HttpStatus.CREATED);
        }
    }

    /**
     * GET  /rest/activate -> activate the registered user.
     */
    @RequestMapping(value = "/rest/activate",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<String> activateAccount(@RequestParam(value = "key") String key) {
        User user = userService.activateRegistration(key);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<String>(user.getLogin(), HttpStatus.OK);
    }

    /**
     * GET  /rest/authenticate -> check if the user is authenticated, and return its login.
     */
    @RequestMapping(value = "/rest/authenticate",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
     * GET  /rest/account -> get the current user.
     */
    @RequestMapping(value = "/rest/account",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<UserDTO> getAccount() {
        User user = userService.getUserWithAuthorities();
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        List<String> roles = new ArrayList<>();
        for (Authority authority : user.getAuthorities()) {
            roles.add(authority.getName());
        }
        return new ResponseEntity<>(
            new UserDTO(
                user.getLogin(),
                null,
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getLangKey(),
                roles,
                null,
                null),
            HttpStatus.OK);
    }

    /**
     * POST  /rest/account -> update the current user information.
     */
    @RequestMapping(value = "/rest/account",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public void saveAccount(@RequestBody UserDTO userDTO) {
        userService.updateUserInformation(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail());
    }

    /**
     * POST  /rest/change_password -> changes the current user's password
     */
    @RequestMapping(value = "/rest/account/change_password",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<?> changePassword(@RequestBody String password) {
        if (StringUtils.isEmpty(password)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        userService.changePassword(password);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * GET  /rest/account/sessions -> get the current open sessions.
     */
    @RequestMapping(value = "/rest/account/sessions",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<PersistentToken>> getCurrentSessions() {
        User user = userRepository.findOne(SecurityUtils.getCurrentLogin());
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(
            persistentTokenRepository.findByUser(user),
            HttpStatus.OK);
    }

    /**
     * DELETE  /rest/account/sessions?series={series} -> invalidate an existing session.
     *
     * - You can only delete your own sessions, not any other user's session
     * - If you delete one of your existing sessions, and that you are currently logged in on that session, you will
     *   still be able to use that session, until you quit your browser: it does not work in real time (there is
     *   no API for that), it only removes the "remember me" cookie
     * - This is also true if you invalidate your current session: you will still be able to use it until you close
     *   your browser or that the session times out. But automatic login (the "remember me" cookie) will not work
     *   anymore.
     *   There is an API to invalidate the current session, but there is no API to check which session uses which
     *   cookie.
     */
    @RequestMapping(value = "/rest/account/sessions/{series}",
            method = RequestMethod.DELETE)
    @Timed
    public void invalidateSession(@PathVariable String series) throws UnsupportedEncodingException {
        String decodedSeries = URLDecoder.decode(series, "UTF-8");
        User user = userRepository.findOne(SecurityUtils.getCurrentLogin());
        List<PersistentToken> persistentTokens = persistentTokenRepository.findByUser(user);
        for (PersistentToken persistentToken : persistentTokens) {
            if (StringUtils.equals(persistentToken.getSeries(), decodedSeries)) {
                persistentTokenRepository.delete(decodedSeries);
            }
        }
    }

    private String createHtmlContentFromTemplate(final User user, final Locale locale, final HttpServletRequest request,
                                                 final HttpServletResponse response) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("user", user);
        variables.put("baseUrl", request.getScheme() + "://" +   // "http" + "://
                                 request.getServerName() +       // "myhost"
                                 ":" + request.getServerPort());
        IWebContext context = new SpringWebContext(request, response, servletContext,
                locale, variables, applicationContext);
        return templateEngine.process("activationEmail", context);
    }
}
