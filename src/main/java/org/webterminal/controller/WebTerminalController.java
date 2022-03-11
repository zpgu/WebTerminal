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

import org.webterminal.connection.Connection;
import static org.webterminal.constant.Constants.*;
import org.webterminal.pojo.TerminalSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webterminal.pojo.TokenRequest;
import org.webterminal.pojo.TokenResponse;
import com.rits.cloning.Cloner;
import java.util.Map;
import java.util.UUID;
//import com.fasterxml.uuid.EthernetAddress;
//import com.fasterxml.uuid.Generators;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.webterminal.service.WebTerminalService;
import static org.webterminal.service.impl.WebTerminalServiceImpl.tokenToRootConnection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Controller
public class WebTerminalController {

    private static final Logger logger = LoggerFactory.getLogger(WebTerminalController.class);
    private static final Map<String, TokenRequest> tokenMap = new ConcurrentHashMap<>();
    private static final DateTimeFormatter parser = ISODateTimeFormat.dateTimeNoMillis();

    @Autowired
    private WebTerminalService webTerminalService;

    @Value("${webterminal.tokenTTL}")
    private int tokenTTL;

    /**
     *
     * @param treq
     * @param request
     * @return
     */
    @RequestMapping(value = "/internal/token", method = RequestMethod.POST)
    @ResponseBody
    public TokenResponse token(@RequestBody TokenRequest treq, HttpServletRequest request) {
        TokenResponse resp;
        logger.debug("/internal/token req from {} with {}", request.getRemoteAddr(), treq.toString());
        // TOOD: better sanity check of treq?

        // minimal required:
        //   host
        //   username if ssh
        //
        //   force usePty to true if ssh and password not provided, or tn3270
        //
        if ((treq.getSessionType() != null
                && (treq.getSessionType().equalsIgnoreCase(JOIN)
                || treq.getSessionType().equalsIgnoreCase(TAKE)
                || treq.getSessionType().equalsIgnoreCase(WATCH))
                && StringUtils.isNotEmpty(treq.getParentToken()))
                || StringUtils.isNotEmpty(treq.getHost())
                && (treq.getConnectionType().equalsIgnoreCase(TELNET_LC)
                || treq.getConnectionType().equalsIgnoreCase(TN3270_LC)
                || treq.getConnectionType().equalsIgnoreCase(SSH_LC)
                && StringUtils.isNotEmpty(treq.getUsername()))) {

            // overwrite in time always
            String tstr = new DateTime().toString(parser);
            treq.setInTime(tstr);

            if (StringUtils.isEmpty(treq.getSessionType())) {
                treq.setSessionType(NEW);
            }

            if (okToIssueToken(treq)) {
                String token = UUID.randomUUID().toString();
                // String token = Generators.timeBasedGenerator( EthernetAddress.fromInterface() ).generate().toString();

                tokenMap.put(token, treq);

                logger.debug("TOKEN: {} returned for {}", token, treq.toString());

                resp = new TokenResponse("success", token);
                return resp;
            }
        }
        resp = new TokenResponse("failure", "Invalid Request");

        return resp;
    }

    /**
     *
     * @param token
     * @param request
     * @return
     */
    @GetMapping("/session")
    public ModelAndView session(@RequestParam(name = "token", required = true) String token, HttpServletRequest request) {

        TokenRequest sreq = tokenMap.get(token);
        if (null != sreq) {
            boolean resumeSuspended = false;
            ModelAndView model = new ModelAndView("session");

            boolean is3270 = false;
            int rows = 24;
            int cols = 80;

            // add to type about sessionType (related to NEW/JOIN/TAKE/VIEW)
            // format  NEW/JOIN/TAKE/WATCH : Desc : host:port / ssh/telnet/tn3270
            if (!sreq.getSessionType().equalsIgnoreCase(NEW)) {
                Connection connection = tokenToRootConnection(sreq.getParentToken());
                if (connection != null) {
                    String type = connection.isSuspended() ? "RESUME" : sreq.getSessionType();

                    // can suspend this conn if it's resuming a conn
                    resumeSuspended = connection.isSuspended();

                    is3270 = connection.getTerminalSessionInfo().getConnectionType().equals("tn3270");
                    rows = connection.getTerminalSessionInfo().getRows();
                    cols = connection.getTerminalSessionInfo().getCols();

                    if (StringUtils.isNotEmpty(connection.getTerminalSessionInfo().getDescription())) {
                        model.addObject("title", type + "  ["
                                + connection.getTerminalSessionInfo().getDescription() + "] "
                                + connection.getTerminalSessionInfo().getHost() + ":"
                                + connection.getTerminalSessionInfo().getPort().toString() + "/"
                                + connection.getTerminalSessionInfo().getConnectionType());
                    } else {
                        model.addObject("title", type + "  "
                                + connection.getTerminalSessionInfo().getHost() + ":"
                                + connection.getTerminalSessionInfo().getPort().toString() + "/"
                                + connection.getTerminalSessionInfo().getConnectionType());
                    }
                    // inherit fontSize too
                    sreq.setFontSize(connection.getTerminalSessionInfo().getFontSize());
                } else {
                    model.addObject("title", "ERROR: Invalid Request (Session Not Found)");
                }
            } else { // NEW
                if (sreq.getPort() == null) {
                    if (sreq.getConnectionType().equalsIgnoreCase(SSH_LC)) {
                        sreq.setPort(22);
                    } else {
                        sreq.setPort(23);
                    }
                }
                if (sreq.getFontSize() == null) {
                    sreq.setFontSize(FONTSIZE);
                }

                is3270 = sreq.getConnectionType().equals("tn3270");

                if (StringUtils.isNotEmpty(sreq.getDescription())) {
                    model.addObject("title", sreq.getSessionType() + "  ["
                            + sreq.getDescription() + "] "
                            + sreq.getHost() + ":"
                            + sreq.getPort().toString() + "/"
                            + sreq.getConnectionType());
                } else {
                    model.addObject("title", sreq.getSessionType() + "  "
                            + sreq.getHost() + ":"
                            + sreq.getPort().toString() + "/"
                            + sreq.getConnectionType());
                }
            }

            boolean canSuspend = resumeSuspended
                    || sreq.getSessionType().equalsIgnoreCase(NEW)
                    || sreq.getSessionType().equalsIgnoreCase(TAKE);

            model.addObject("canSuspend", canSuspend);

            model.addObject("token", token);
            model.addObject("fontSize", sreq.getFontSize());

            model.addObject("resize", !is3270 && canSuspend);
            model.addObject("rows", rows);
            model.addObject("cols", cols);

            logger.debug("/session model {}", model.toString());
            logger.debug("/session req {} from {} with VALID token {}", sreq, request.getRemoteAddr(), token);

            request.getSession().setMaxInactiveInterval(-1);

            return model;
        } else {
            ModelAndView error = new ModelAndView("tokenNotFound");
            error.addObject("token", token);

            logger.info("/session req from {} with INVALID token {}", request.getRemoteAddr(), token);

            return error;
        }
    }

    /**
     *
     * @param token
     * @param webUserName
     * @param webUserRole
     * @return
     */
    @RequestMapping(value = "/internal/drop", method = RequestMethod.GET)
    @ResponseBody
    public Map drop(
            @RequestParam(name = "token", required = true) String token,
            @RequestParam(name = "webUserName", required = true) String webUserName,
            @RequestParam(name = "webUserRole", required = true) String webUserRole
    ) {
        logger.debug("/internal/drop session with token {}", token);

        boolean verified = false;

        // ACL:
        // webUserRole "ROLE_ADMIN"
        // token's connection getWebUserName() same as webUserName
        if ("ROLE_ADMIN".equals(webUserRole)) {
            verified = true;
        } else {
            Connection connection = tokenToRootConnection(token);
            if (connection != null
                    && connection.getTerminalSessionInfo().getWebUserName().equals(webUserName)) {
                verified = true;
            }
        }

        if (verified) {
            return webTerminalService.dropSession(token);
        } else {
            return Collections.singletonMap("status", "FAILURE: KILL " + token + " NOT ALLOWED");
        }
    }

    /**
     *
     * @return
     */
    @RequestMapping(value = "/internal/pending", method = RequestMethod.GET)
    @ResponseBody
    public Map pendingTokens() {
        Cloner cloner = new Cloner();
        Map<String, TokenRequest> tmpMap = cloner.deepClone(tokenMap);

        tmpMap.entrySet().forEach((Map.Entry<String, TokenRequest> entry) -> {
            TokenRequest obj = entry.getValue();
            if (obj.getPassword() != null) {
                obj.setPassword("*");
            }
        });

        return tmpMap;
    }

    /**
     *
     * @param webUserName
     * @param request
     * @return
     */
    @RequestMapping(value = "/internal/sessions", method = RequestMethod.GET)
    @ResponseBody
    public List<TerminalSessionInfo> visibleSessions(
            @RequestParam(name = "user", required = false) String webUserName,
            HttpServletRequest request) {
        logger.debug("/internal/sessions req from {} for {}",
                request.getRemoteAddr(), webUserName == null ? "ALL" : webUserName);

        return webTerminalService.getTerminalSessionInfoList(webUserName);
    }

    private boolean okToIssueToken(TokenRequest treq) {
        boolean verified = false;

        if (treq != null) {
            // ACL:
            // treq.getWebUserRole() "ROLE_ADMIN"
            // parentConnection getWebUserName() same as treq.getWebUserName()
            //   or parentConnection isVisibleToAll
            if ("ROLE_ADMIN".equals(treq.getWebUserRole())) {
                verified = true;
            } else {
                if (treq.getParentToken() != null) {
                    Connection connection = tokenToRootConnection(treq.getParentToken());
                    if (connection != null
                            && (connection.getTerminalSessionInfo().getWebUserName().equals(treq.getWebUserName())
                            || connection.getTerminalSessionInfo().isVisibleToAll())) {
                        verified = true;
                    }
                } else {
                    verified = true;
                }
            }
        }

        return verified;
    }

    /**
     *
     * @param token
     * @return
     */
    public static boolean tokenExists(String token) {
        return tokenMap.containsKey(token);
    }

    /**
     *
     * @param token
     * @return
     */
    public static TokenRequest tokenToRequest(String token) {
        return tokenMap.get(token);
    }

    /**
     *
     * @param token
     */
    public static void tokenRemove(String token) {
        tokenMap.remove(token);
    }

    /**
     *
     */
    @Scheduled(cron = "${webterminal.cronTokens}")
    public void tokenHouseKeeping() {
        logger.trace("tokenHouseKeeping run, tokenTTL={}", tokenTTL);

        DateTime now = new DateTime();
        tokenMap.entrySet()
                .removeIf((Map.Entry<String, TokenRequest> entry) -> {
                    TokenRequest req = entry.getValue();

                    DateTime dt = parser.parseDateTime(req.getInTime());
                    Seconds seconds = Seconds.secondsBetween(dt, now);
                    logger.trace("{} is {} seconds old", entry.getKey(), seconds.getSeconds());
                    return (seconds.getSeconds() > tokenTTL);
                });

        logger.trace("tokenHouseKeeping end");
    }

    // TODO: more endpoints:
    //
    //is someone connected to a particular device?
    //all connections to a particular device?
    //all connections by someone?
}
