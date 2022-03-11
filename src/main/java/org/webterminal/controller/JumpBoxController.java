/* part of WebTerminal project

   Copyright (C) 2022  ZP Gu. All rights reserved.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.webterminal.controller;

import static org.webterminal.constant.Constants.*;
import org.webterminal.pojo.TerminalSessionInfo;
import org.webterminal.pojo.TokenRequest;
import org.webterminal.pojo.TokenResponse;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
//import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.ssl.SSLContexts;
//import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@Controller
public class JumpBoxController {

    private static final Logger logger = LoggerFactory.getLogger(JumpBoxController.class);
    private static final List<String> listConnectionType = Arrays.asList(SSH_LC, TELNET_LC, TN3270_LC);
    private static final List<String> listAuditLogging = Arrays.asList("OFF", "ON");

    private RestTemplate restTemplate = null;
    private RestTemplate restTemplatSSL = null;

    /**
     *
     * @param model
     * @return
     */
    @GetMapping("/restricted/jumpbox")
    public String jumpbox(Model model) {
        TokenRequest request = new TokenRequest();
        model.addAttribute("request", request);
        model.addAttribute("connectionType", listConnectionType);
        model.addAttribute("auditLogging", listAuditLogging);
        model.addAttribute("message", "Host required; Username required for ssh sessions");

        return "jumpbox";
    }

    /**
     *
     * @param treq
     * @param model
     * @param authentication
     * @param httpRequest
     * @return
     */
    @PostMapping("/restricted/go")
    public String go(@ModelAttribute("request") TokenRequest treq, Model model,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        // UI side should fill more into request
        treq.setWebUserIp(httpRequest.getRemoteAddr());
        treq.setWebUserName(authentication.getName());
        treq.setWebUserRole(getRole(authentication.getAuthorities()));

        logger.debug("/restricted/go req with {}", treq);

        // this is to show token request as an outside REST client
        // could have called internal controller directly in the same app
        String url = httpRequest.getScheme() + "://localhost:" + httpRequest.getServerPort() + "/internal/token";
        logger.debug("REST call {} to get token", url);

        RestTemplate myTemplate = getRestTemplate(httpRequest.getScheme());
        HttpEntity<TokenRequest> request = new HttpEntity<>(treq);
        TokenResponse response = myTemplate.postForObject(url, request, TokenResponse.class);

        logger.debug("REST /internal/token response {}", response);

        if (response != null && response.getStatus().equalsIgnoreCase("success")) {
            return String.format("redirect:/session?token=" + response.getPayload());
        } else {
            logger.debug("/restricted/go back to jumpbox on REST call failure");

            model.addAttribute("request", treq);
            model.addAttribute("connectionType", listConnectionType);
            model.addAttribute("message", "Request failed: "
                    + (response != null ? response.getPayload() : "REST call failure")
                    + ". Please check your input: Host required; Username required for ssh sessions");

            logger.debug("/restricted/go model {}", model.toString());

            return "jumpbox";
        }
    }

    /**
     *
     * @param status
     * @param model
     * @param authentication
     * @param httpRequest
     * @return
     * @throws URIException
     */
    @GetMapping("/restricted/sessions")
    public String sessions(@RequestParam(name = "status", required = false) String status, Model model,
            Authentication authentication,
            HttpServletRequest httpRequest) throws URIException {

        String userRole;
        // this is to show sessions request as an outside REST client
        // could have called internal controller directly in the same app
        String url;
        if (hasRole("ROLE_ADMIN", authentication.getAuthorities())) {
            // fetch all
            url = httpRequest.getScheme() + "://localhost:" + httpRequest.getServerPort() + "/internal/sessions";
            userRole = "ADMIN";
        } else {
            // fetch only belong to user
            url = httpRequest.getScheme() + "://localhost:" + httpRequest.getServerPort() + "/internal/sessions?user="
                    + URIUtil.encodeWithinQuery(authentication.getName());
            userRole = "USER";
        }
        logger.debug("REST call {} to get sessions", url);

        RestTemplate myTemplate = getRestTemplate(httpRequest.getScheme());
        TerminalSessionInfo[] response = myTemplate.getForObject(url, TerminalSessionInfo[].class);

        logger.debug("REST /internal/sessions response {}", Arrays.toString(response));

        if (response != null) {
            // sort it for UI
            Comparator<TerminalSessionInfo> compareBy = new Comparator<TerminalSessionInfo>() {
                @Override
                public int compare(TerminalSessionInfo t1, TerminalSessionInfo t2) {
                    logger.trace("compare {}, {}", t1, t2);

                    // NEW        NEW
                    if (t1.getSessionType().equalsIgnoreCase(NEW) && t2.getSessionType().equalsIgnoreCase(NEW)) {
                        return (t1.getInTime()).compareTo(t2.getInTime());
                    } else // NEW        JOIN/WATCH
                    if (t1.getSessionType().equalsIgnoreCase(NEW) && !t2.getSessionType().equalsIgnoreCase(NEW)) {
                        return (t1.getInTime() + t1.getInTime()).compareTo(t2.getParent().getInTime() + t2.getInTime());
                    } else // JOIN/WATCH NEW
                    if (!t1.getSessionType().equalsIgnoreCase(NEW) && t2.getSessionType().equalsIgnoreCase(NEW)) {
                        return (t1.getParent().getInTime() + t1.getInTime()).compareTo(t2.getInTime() + t2.getInTime());
                    } else // JOIN/WATCH JOIN/WATCH
                    //if (!t1.getSessionType().equalsIgnoreCase(NEW) && !t2.getSessionType().equalsIgnoreCase(NEW)) {
                    {
                        return (t1.getParent().getInTime() + t1.getInTime()).compareTo(t2.getParent().getInTime() + t2.getInTime());
                    }
                }
            };
            Arrays.sort(response, compareBy);

            logger.trace("/restricted/sessions sorted {}", Arrays.toString(response));

            if ("invalid".equals(status)) {
                model.addAttribute("status", "!!! Previous Action Not Valid, Return to Session List !!!");
            } else {
                model.addAttribute("status", "");
            }
            model.addAttribute("sessions", response);
            model.addAttribute("userId", authentication.getName());
            model.addAttribute("userRole", userRole);

            DateTimeFormatter parser = ISODateTimeFormat.dateTimeNoMillis();
            String tstr = new DateTime().toString(parser);
            model.addAttribute("title", authentication.getName() + ": Available Sessions @" + tstr);

            logger.debug("/restricted/sessions model {}", model.toString());
        }

        return "sessions";
    }

    /**
     *
     * @param token
     * @param mode
     * @param authentication
     * @param httpRequest
     * @return
     */
    @GetMapping("/restricted/join")
    public String join(
            @RequestParam(name = "token", required = true) String token,
            @RequestParam(name = "mode", required = true) String mode,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        logger.debug("/restricted/join req with {} {}", token, mode);

        TokenRequest treq = new TokenRequest();
        // bare minimal
        treq.setSessionType(mode);
        treq.setParentToken(token);

        // UI side should fill in more available info about end user
        treq.setWebUserIp(httpRequest.getRemoteAddr());
        treq.setWebUserName(authentication.getName());
        treq.setWebUserRole(getRole(authentication.getAuthorities()));

        // this is to show token request as an outside REST client
        // could have called internal controller directly in the same app
        String url = httpRequest.getScheme() + "://localhost:" + httpRequest.getServerPort() + "/internal/token";
        logger.debug("REST call {} to get token", url);

        RestTemplate myTemplate = getRestTemplate(httpRequest.getScheme());
        HttpEntity<TokenRequest> request = new HttpEntity<>(treq);
        TokenResponse response = myTemplate.postForObject(url, request, TokenResponse.class);

        logger.debug("REST /internal/token response {}", response);

        if (response != null && response.getStatus().equalsIgnoreCase("success")) {
            return String.format("redirect:/session?token=" + response.getPayload());
        } else {
            logger.debug("back to /sessions on REST call rejection");
            return String.format("redirect:/restricted/sessions?status=invalid");
        }
    }

    /**
     *
     * @param token
     * @param authentication
     * @param httpRequest
     * @return
     */
    @GetMapping("/restricted/drop")
    @ResponseBody
    public Map drop(
            @RequestParam(name = "token", required = true) String token,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        logger.debug("/restricted/drop token {}", token);

        String webUserName = authentication.getName();
        String webUserRole = getRole(authentication.getAuthorities());

        // this is to show drop request as an outside REST client
        // could have called internal controller directly in the same app
        String url = httpRequest.getScheme() + "://localhost:" + httpRequest.getServerPort()
                + "/internal/drop?token={token}&webUserName={webUserName}&webUserRole={webUserRole}";
        logger.debug("REST call {} to drop token", url);

        RestTemplate myTemplate = getRestTemplate(httpRequest.getScheme());
        Map response = myTemplate.getForObject(url, Map.class, token, webUserName, webUserRole);

        logger.debug("REST /internal/drop response {}", response);

        return response;
    }

    /**
     *
     * @param scheme
     * @return
     */
    public RestTemplate getRestTemplate(String scheme) {
        if (scheme.equalsIgnoreCase("http")) {
            if (this.restTemplate == null) {
                this.restTemplate = new RestTemplate();
            }
            return this.restTemplate;
        } else {
            if (this.restTemplatSSL == null) {
                TrustAllStrategy acceptingTrustStrategy = new TrustAllStrategy();
                SSLContext sslContext;
                try {
                    sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
                    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
                            NoopHostnameVerifier.INSTANCE);

                    Registry<ConnectionSocketFactory> socketFactoryRegistry
                            = RegistryBuilder.<ConnectionSocketFactory>create()
                                    .register("https", sslsf)
                                    .register("http", new PlainConnectionSocketFactory())
                                    .build();

                    BasicHttpClientConnectionManager connectionManager
                            = new BasicHttpClientConnectionManager(socketFactoryRegistry);
                    CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf)
                            .setConnectionManager(connectionManager).build();

                    HttpComponentsClientHttpRequestFactory requestFactory
                            = new HttpComponentsClientHttpRequestFactory(httpClient);
                    this.restTemplatSSL = new RestTemplate(requestFactory);

                } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
                    logger.error("Error setting up SSL RestTemplate {}", ex);
                }
            }
            return this.restTemplatSSL;
        }
    }

    private boolean hasRole(String role, Collection<? extends GrantedAuthority> authorities) {
        for (GrantedAuthority authority : authorities) {
            logger.trace("check {} against {}", role, authority);
            if (authority.getAuthority().equalsIgnoreCase(role)) {
                return true;
            }
        }
        return false;
    }

    private String getRole(Collection<? extends GrantedAuthority> authorities) {
        for (GrantedAuthority authority : authorities) {
            logger.trace("found {}", authority);
            return authority.getAuthority();
        }
        return "ROLE_USER";
    }
}
