package com.mycompany.myapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.mycompany.myapp.domain.*;
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
import org.springframework.social.ApiException;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.UserProfile;
import org.springframework.social.connect.web.ProviderSignInAttempt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.spring4.context.SpringWebContext;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

    private final static String EXTERNAL_AUTH_AS_USERDTO_KEY = "AccountResource.signInAsUserDTO";

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

    /**
     * Build a new Connection to a social provider using the information from a previous
     * sign in.
     * @param attempt a possibly null ProviderSignInAttempt
     * @return null a valid Connection or null if attempt was null or a Connection could not be established
     */
    Connection<?> buildConnection(ProviderSignInAttempt attempt) {
        if (attempt == null)
            return null;

        Connection<?> connection = attempt.getConnection();

        if (!connection.test()) {
            log.warn("Social connection to {} for user '{}' failed", connection.getKey().getProviderId(), connection.getKey().getProviderUserId());
            return null;
        }
        return connection;
    }

    /**
     * Retrieve a previous external social authentication attempt as a UserDTO.
     * @param request a non-null request
     * @return a UserDTO with the firstName, lastName, email, and externalAccount details set or null if
     *  there was no previous social authentication attempt or the details of the social authentication
     *  could not be retrieved.
     *  @throws org.springframework.social.ApiException when the social API does not return the required attributes (name, email)
     *  @see org.springframework.social.connect.web.ProviderSignInAttempt
     *  @see org.springframework.social.security.SocialAuthenticationFilter#addSignInAttempt(javax.servlet.http.HttpSession, org.springframework.social.connect.Connection) SocialAuthenticationFilter#addSignInAttempt
     */
    UserDTO externalAuthAsUserDTO(HttpServletRequest request)
    {
        UserDTO userDTO = (UserDTO) WebUtils.getSessionAttribute(request, EXTERNAL_AUTH_AS_USERDTO_KEY);
        if (userDTO == null) {
            // check if the user was successfully authenticated against an external service
            // but failed to authenticate against this application.
            ProviderSignInAttempt attempt = (ProviderSignInAttempt) WebUtils.getSessionAttribute(request, ProviderSignInAttempt.SESSION_ATTRIBUTE);
            Connection<?> connection = buildConnection(attempt);
            if (connection == null)
                return null;

            // build a new UserDTO from the external provider's version of the User
            UserProfile profile = connection.fetchUserProfile();
            String firstName = profile.getFirstName();
            String lastName = profile.getLastName();
            String email = profile.getEmail();

            // build the ExternalAccount from the ConnectionKey
            String externalAccountProviderName = connection.getKey().getProviderId();
            ExternalAccountProvider externalAccountProvider = ExternalAccountProvider.caseInsensitiveValueOf(externalAccountProviderName);
            String externalUserId = connection.getKey().getProviderUserId();
            ExternalAccount externalAccount = new ExternalAccount(externalAccountProvider, externalUserId);

            // check that we got the information we needed
            if (StringUtils.isBlank(firstName) || StringUtils.isBlank(lastName) || StringUtils.isBlank(email))
                throw new ApiException(externalAccountProviderName, "provider failed to return required attributes");

            userDTO = new UserDTO(firstName, lastName, email, externalAccount);

            // save the new UserDTO for later and clean up the HttpSession
            request.getSession().removeAttribute(ProviderSignInAttempt.SESSION_ATTRIBUTE);
            request.getSession().setAttribute(EXTERNAL_AUTH_AS_USERDTO_KEY, userDTO);

            log.debug("Retrieved details from {} for user '{}'", externalAccountProviderName, externalUserId);
        }
        return userDTO;
    }

    /**
     * Check if the current request is associated with a social registration.
     * @param request a non-null request
     * @return true if the request is associated with a social registration
     */
    boolean isSocialRegistration(HttpServletRequest request) {
        return
            WebUtils.getSessionAttribute(request, EXTERNAL_AUTH_AS_USERDTO_KEY) != null ||
            WebUtils.getSessionAttribute(request, ProviderSignInAttempt.SESSION_ATTRIBUTE) != null;
    }

    /**
     * GET /rest/register -> get the details of an ongoing registration
     * @param request
     * @return 200 OK or 404 if there is no ongoing registration
     */
    @RequestMapping(value = "/rest/register",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<?> getRegisterAccount(HttpServletRequest request) {
        UserDTO dto = externalAuthAsUserDTO(request);

        if (dto != null) {
            log.debug("Returning ongoing registration request");
            return new ResponseEntity<>(dto, HttpStatus.OK);
        }
        else
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    ResponseEntity<?> registerExternalAccount(UserDTO currentRequestDTO, HttpServletRequest request,
                                 HttpServletResponse response) {
        log.debug("Creating user from previous external authentication");

        // get the information from the social provider as a UserDTO
        UserDTO externalAuthDTO = externalAuthAsUserDTO(request);

        // check that there isn't already another account linked to the current external account
        ExternalAccount externalAccount = externalAuthDTO.getExternalAccounts().iterator().next();
        User existingUser = userRepository.getUserByExternalAccount(externalAccount.getExternalProvider(), externalAccount.getExternalId());
        if (existingUser != null)
            return new ResponseEntity<>("The external login is already linked to another User", HttpStatus.BAD_REQUEST);

        // use the email supplied by the user, falling back on the email retrieved from
        // the social login
        String email;
        if (StringUtils.isNotBlank(currentRequestDTO.getEmail()))
            email = currentRequestDTO.getEmail();
        else
            email = externalAuthDTO.getEmail();

        User user = userService.createUserInformation(currentRequestDTO.getLogin(), externalAuthDTO.getFirstName(),
                 externalAuthDTO.getLastName(), email.toLowerCase(), currentRequestDTO.getLangKey(), externalAccount);
        sendActivationEmail(user, request, response);

        // cleanup the social stuff that we've been keeping in the session
        request.getSession().removeAttribute(EXTERNAL_AUTH_AS_USERDTO_KEY);

        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    void sendActivationEmail(User user, HttpServletRequest request,
                             HttpServletResponse response) {
        final Locale locale = Locale.forLanguageTag(user.getLangKey());
        String content = createHtmlContentFromTemplate(user, locale, request, response);
        mailService.sendActivationEmail(user.getEmail(), content, locale);
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
        String login = userDTO.getLogin();

        // check that a login was supplied and the login does not already exist
        if (StringUtils.isBlank(login)) {
            return new ResponseEntity<>("Login must not be null", HttpStatus.BAD_REQUEST);
        }
        else if (userRepository.exists(login)) {
            log.debug("User '{}' already exists", login);
            return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
        }

        if (isSocialRegistration(request)) {
            return registerExternalAccount(userDTO, request, response);
        }
        else {
            // for an internally managed account, email and password are required
            if (StringUtils.isBlank(userDTO.getEmail()))
                return new ResponseEntity<>("Email must not be null", HttpStatus.BAD_REQUEST);
            else if (StringUtils.isBlank(userDTO.getPassword()))
                return new ResponseEntity<>("Password must not be null", HttpStatus.BAD_REQUEST);

            User user = userService.createUserInformation(login, userDTO.getPassword(), userDTO.getFirstName(),
                    userDTO.getLastName(), userDTO.getEmail().toLowerCase(), userDTO.getLangKey());
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
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getLangKey(),
                roles,
                user.getExternalAccounts()),
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
