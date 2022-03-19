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
package org.webterminal.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.webterminal.util.AuditLoggingToFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSchException;
import org.webterminal.connection.Connection;
import org.webterminal.connection.impl.JoinConnection;
import org.webterminal.connection.impl.PtyConnection;
import org.webterminal.connection.impl.SshConnection;
import org.webterminal.connection.impl.TelnetConnection;
import org.webterminal.constant.Constants;
import static org.webterminal.constant.Constants.CLIENT_CONNECT;
import static org.webterminal.constant.Constants.CLIENT_SUSPEND;
import static org.webterminal.constant.Constants.CLIENT_DISCONNECT;
import static org.webterminal.constant.Constants.CLIENT_DATA;
import static org.webterminal.constant.Constants.NEW;
import static org.webterminal.constant.Constants.SESSION_UUID;
import static org.webterminal.constant.Constants.SSH_LC;
import static org.webterminal.constant.Constants.TELNET_LC;
import static org.webterminal.constant.Constants.TN3270_LC;
import org.webterminal.controller.WebTerminalController;
import org.webterminal.pojo.RowsCols;
import org.webterminal.pojo.TokenRequest;
import org.webterminal.pojo.TerminalSessionInfo;
import org.webterminal.pojo.TwoWayMessage;
import org.webterminal.pojo.TokenRowsCols;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.webterminal.service.WebTerminalService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.BinaryMessage;
import static org.webterminal.constant.Constants.CLIENT_RESIZE;
//import com.fasterxml.uuid.EthernetAddress;
//import com.fasterxml.uuid.Generators;

@Service
public class WebTerminalServiceImpl implements WebTerminalService {

    private static final Map<String, Connection> SessionMAP = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(WebTerminalServiceImpl.class);
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    // thread safe
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${webterminal.auditLogDir}")
    private String auditLogDir;

    /**
     *
     * @param session
     */
    @Override
    public void initSession(WebSocketSession session) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        //String sessionId = Generators.timeBasedGenerator( EthernetAddress.fromInterface() ).generate().toString().replace("-", "");
        session.getAttributes().put(SESSION_UUID, sessionId);
        logger.debug("initSession attributes: {}", session.getAttributes().toString());
    }

    /**
     *
     * @param buffer
     * @param webSocketSession
     */
    @Override
    public void textMessageHandler(String buffer, WebSocketSession webSocketSession) {
        TwoWayMessage clientMessage;
        try {
            clientMessage = objectMapper.readValue(buffer, TwoWayMessage.class);
        } catch (IOException ex) {
            logger.error("TwoWayMessage JSON Conversion exception: {}", ex.toString());

            WebTerminalServiceImpl.sendOOBMessage(webSocketSession, ex);
            sessionClose(webSocketSession);
            return;
        }
        logger.trace("in textMessageHandler: {}", clientMessage.toString());

        String sessionId = String.valueOf(webSocketSession.getAttributes().get(SESSION_UUID));

        if (CLIENT_CONNECT.equals(clientMessage.getT())) {
            logger.debug("CONN textMessageHandler clientMessage: {}", clientMessage.toString());

            TokenRowsCols tokenRowsCols;
            try {
                tokenRowsCols = objectMapper.readValue(clientMessage.getP(), TokenRowsCols.class);
            } catch (IOException ex) {
                logger.error("connect TokenRowsCols JSON Conversion exception: {}", ex.toString());

                WebTerminalServiceImpl.sendOOBMessage(webSocketSession, ex);
                sessionClose(webSocketSession);
                return;
            }

            TokenRequest req = WebTerminalController.tokenToRequest(tokenRowsCols.getToken());
            if (req != null) {
                TerminalSessionInfo terminalSessionInfo = new TerminalSessionInfo();

                // transfer into terminalSessionInfo
                terminalSessionInfo.transferFromTokenRequest(req);
                terminalSessionInfo.setToken(tokenRowsCols.getToken());

                terminalSessionInfo.setRows(tokenRowsCols.getRows());
                terminalSessionInfo.setCols(tokenRowsCols.getCols());

                // remove so token is for one time use only
                WebTerminalController.tokenRemove(tokenRowsCols.getToken());

                final Connection connection;

                if (!terminalSessionInfo.getSessionType().equals(NEW)) {
                    connection = new JoinConnection(webSocketSession, terminalSessionInfo,
                            new AuditLoggingToFile(auditLogDir));
                } else if (terminalSessionInfo.isUsePty()) {
                    // extra buf for tn3270
                    connection = new PtyConnection(webSocketSession, terminalSessionInfo,
                            terminalSessionInfo.getConnectionType().equalsIgnoreCase(TN3270_LC)
                            ? new AuditLoggingToFile(auditLogDir, 24 * 80 * 2)
                            : new AuditLoggingToFile(auditLogDir)
                    );
                } else if (terminalSessionInfo.getConnectionType().equalsIgnoreCase(SSH_LC)) {
                    connection = new SshConnection(webSocketSession, terminalSessionInfo,
                            new AuditLoggingToFile(auditLogDir));
                } else if (terminalSessionInfo.getConnectionType().equalsIgnoreCase(TELNET_LC)) {
                    connection = new TelnetConnection(webSocketSession, terminalSessionInfo,
                            new AuditLoggingToFile(auditLogDir));
                } else {
                    logger.error("unknown connectionType: {}", terminalSessionInfo.getConnectionType());

                    sendOOBMessage(webSocketSession,
                            "Unknown ConnectionType: " + terminalSessionInfo.getConnectionType());
                    sessionClose(webSocketSession);
                    return;
                }

                SessionMAP.put(sessionId, connection);
                terminalSessionInfo.setSessionId(sessionId);
                logger.debug("session setup: {}", terminalSessionInfo);

                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            connection.connect();
                        } catch (JSchException | IOException | InterruptedException ex) {
                            logger.debug("connect side exception: {}, {}", ex.getMessage(), ex.getStackTrace());

                            sendRootCauseMessage(connection, ex);
                            connectionClose(connection);
                        }
                        logger.debug("connect ended: {}", terminalSessionInfo);
                    }
                });
            } else {
                logger.error("token not found: {}", tokenRowsCols.getToken());

                sendOOBMessage(webSocketSession, "Session Token Not Found");
                sessionClose(webSocketSession);
            }

        } else if (CLIENT_DATA.equals(clientMessage.getT())) {
            String payload = clientMessage.getP();
            Connection connection = SessionMAP.get(sessionId);

            if (connection != null) {
                try {
                    if (connection.getTerminalSessionInfo().isReady()) {
                        connection.send(payload);
                    } else {
                        logger.info("user input ignored while session not ready");
                    }
                } catch (IOException ex) {
                    logger.warn("send into destination exception: {}", ex.getMessage());

                    sendRootCauseMessage(connection, ex);
                    sessionClose(webSocketSession);
                }
            } else {
                sendOOBMessage(webSocketSession, "Unknown Session for Data");
                sessionClose(webSocketSession);
            }
        } else if (CLIENT_SUSPEND.equals(clientMessage.getT())) {
            logger.debug("suspend received: {}", clientMessage);
            Connection connection = tokenToRootConnection(clientMessage.getP());

            // match toplevel session, can only suspend own session, session is NEW type
            if (connection != null
                    && connection.getTerminalSessionInfo().getSessionId().equals(sessionId)
                    && connection.getTerminalSessionInfo().getSessionType().equalsIgnoreCase("NEW")) {
                logger.debug("ok to suspend {}", clientMessage);

                connection.suspend();
                // no idle timeout, otherwise defeats purpose of suspend as connection will be reaped
                connection.getTerminalSessionInfo().setMaxIdleTime(0);
            }
        } else if (CLIENT_DISCONNECT.equals(clientMessage.getT())) {
            logger.debug("disconnect received: {}", clientMessage);
            Connection connection = tokenToConnection(clientMessage.getP());

            // disconnect own session
            if (connection != null
                    && connection.getTerminalSessionInfo().getSessionId().equals(sessionId)) {
                logger.debug("ok to disconnect {}", clientMessage);

                sendOOBMessage(webSocketSession, "Session Disconnecting");
                connectionClose(connection);
            }  else {
                sendOOBMessage(webSocketSession, "Unknown Session for Disconnect");
                sessionClose(webSocketSession);
            }
        } else if (CLIENT_RESIZE.equals(clientMessage.getT())) {
            logger.debug("resize received {}", clientMessage);

            Connection connection = SessionMAP.get(sessionId);
            if (connection != null) {
                RowsCols rowsCols;
                try {
                    rowsCols = objectMapper.readValue(clientMessage.getP(), RowsCols.class);
                    connection.resize(rowsCols);
                } catch (IOException ex) {
                    logger.error("resize RowsCols JSON Conversion exception: {}", ex.toString());

                    sendRootCauseMessage(connection, ex);
                    sessionClose(webSocketSession);
                }
            } else {
                sendOOBMessage(webSocketSession, "Unknown Session for Resize");
                sessionClose(webSocketSession);
            }
        } else {
            logger.error("unsupported client message: {}", clientMessage);

            sendOOBMessage(webSocketSession, "Unsupported Message: " + clientMessage);
            sessionClose(webSocketSession);
        }
    }

    //
    // utilities from this point on, should put in a separate class?
    //
    /**
     *
     * @param session
     */
    public static void sessionClose(WebSocketSession session) {
        String sessionId = String.valueOf(session.getAttributes().get(SESSION_UUID));
        Connection connection = SessionMAP.get(sessionId);
        // webSocketHandler calls when connection suspends, so leave it alone if suspended
        if (connection != null && !connection.isSuspended()) {
            try {
                // need to close all child sessions too
                ArrayList<String> children = connection.getTerminalSessionInfo().getChildren();

                if (children != null) {
                    connection.getTerminalSessionInfo().setChildren(null);

                    for (String child : children) {
                        Connection childconn = SessionMAP.get(child);
                        if (childconn != null) {
                            logger.debug("ALSO close child: {}", child);

                            childconn.sendToUser("\r\nDisconnecting(Parent Session Closed)\r\n");
                            childconn.close();
                            childconn.webSocketSessionClose();

                            SessionMAP.remove(child);
                        }
                    }

                }
                connection.close();
                connection.webSocketSessionClose();
            } catch (IOException e) {
                logger.error("session close exception: {}", e.getMessage());
            }

            SessionMAP.remove(sessionId);
        } else if (connection == null) {
            try {
                session.close();
            } catch (IOException ex) {
            }
        }
    }

    /**
     * complete teardown
     *
     * @param connection
     */
    public static void connectionClose(Connection connection) {
        String sessionId = connection.getTerminalSessionInfo().getSessionId();

        try {
            // need to close all child sessions too
            ArrayList<String> children = connection.getTerminalSessionInfo().getChildren();

            if (children != null) {
                connection.getTerminalSessionInfo().setChildren(null);

                for (String child : children) {
                    Connection childconn = SessionMAP.get(child);
                    if (childconn != null) {
                        logger.debug("ALSO close child conn: {}", child);

                        childconn.sendToUser("\r\nDisconnecting(Parent Session Closed)\r\n");
                        childconn.close();
                        childconn.webSocketSessionClose();

                        SessionMAP.remove(child);
                    }
                }

            }
            connection.close();
            connection.webSocketSessionClose();

        } catch (IOException e) {
            logger.error("connection close exception: {}", e.getMessage());
        }

        SessionMAP.remove(sessionId);
    }

    /**
     * used for suspend
     *
     * @param terminalSessionInfo
     */
    public static void disconnectChildren(TerminalSessionInfo terminalSessionInfo) {
        ArrayList<String> children = terminalSessionInfo.getChildren();
        if (children != null) {
            terminalSessionInfo.setChildren(null);
            try {
                for (String child : children) {
                    Connection childconn = SessionMAP.get(child);
                    if (childconn != null) {
                        logger.debug("disconnect child: {}", child);

                        childconn.sendToUser("\r\nDisconnecting(Parent Session Suspended)\r\n");
                        childconn.webSocketSessionClose();

                        SessionMAP.remove(child);
                    }
                }
            } catch (IOException ex) {
            }
        }
    }

    /**
     *
     * @param throwable
     * @return
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable.getCause() != null) {
            return getRootCause(throwable.getCause());
        }
        return throwable;
    }

    /**
     *
     * @param session
     * @param throwable
     */
    private static void sendOOBMessage(WebSocketSession session, Throwable throwable) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(toClientString(Constants.CR_LF + getRootCause(throwable).getMessage() + Constants.CR_LF));
            } catch (IOException ex) {
            }
        }
    }

    /**
     *
     * @param session
     * @param message
     */
    private static void sendOOBMessage(WebSocketSession session, String message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(toClientString(Constants.CR_LF + message + Constants.CR_LF));
            } catch (IOException ex) {
            }
        }
    }

    private static void sendRootCauseMessage(Connection connection, Throwable throwable) {
        if (connection != null) {
            try {
                connection.sendToUser(Constants.CR_LF + getRootCause(throwable).getMessage() + Constants.CR_LF);
            } catch (IOException ex) {
            }
        }
    }

    /**
     *
     * @param webUserName
     * @return
     */
    @Override
    public List<TerminalSessionInfo> getTerminalSessionInfoList(String webUserName) {
        List<TerminalSessionInfo> list = new ArrayList<>();

        SessionMAP.entrySet().forEach((Map.Entry<String, Connection> entry) -> {
            Connection connection = entry.getValue();
            // skip connections not connected yet, avoid exposing password
            if (connection.getTerminalSessionInfo().getPassword() == null
                    || connection.getTerminalSessionInfo().getPassword().equals("*")) {
                // webUserName null means get all
                if (StringUtils.isEmpty(webUserName)
                        || connection.getTerminalSessionInfo().isVisibleToAll()
                        || webUserName.equals(connection.getTerminalSessionInfo().getWebUserName())) {
                    list.add(connection.getTerminalSessionInfo());
                }
            }
        });

        return list;
    }

    /**
     *
     * @param token
     * @return
     */
    @Override
    public Map dropSession(String token) {
        logger.debug("REQ to dropSession with TOKEN: {}", token);

        Connection connection = null;
        Iterator<Map.Entry<String, Connection>> iterator = SessionMAP.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Connection> entry = iterator.next();

            connection = entry.getValue();
            if (token.equals(connection.getTerminalSessionInfo().getToken())) {
                break;
            } else {
                connection = null;
            }
        }

        if (connection != null) {
            try {
                connection.sendToUser("\r\nSession Terminated Administratively");
            } catch (IOException ex) {
            }

            connectionClose(connection);

            return Collections.singletonMap("status", "SUCCESS: " + token + " REMOVED");
        } else {
            return Collections.singletonMap("status", "FAILURE: " + token + " NOT FOUND");
        }
    }

    /**
     * cronjob to kick out idle (if set) sessions
     */
    @Scheduled(cron = "${webterminal.cronSessions}")
    public void sessionIdleHouseKeeping() {
        logger.trace("sessionIdleHouseKeeping run");

        long now = System.currentTimeMillis() / 1000l;

        /*
        SessionMAP.entrySet().forEach((Map.Entry<String, Connection> entry) -> {
            Connection connection = entry.getValue();

            // only NEW sessions are involved here
            if (connection.getTerminalSessionInfo().getParent() == null) {
                int max = connection.getTerminalSessionInfo().getMaxIdleTime();
                if (max > 0 && connection.getTerminalSessionInfo().getLastTrafficTime() != 0) {
                    // in seconds
                    max = 60 * max;
                    if ((now - connection.getTerminalSessionInfo().getLastTrafficTime()) >= max) {
                        logger.debug("TIMING out: {}", connection.getTerminalSessionInfo());
                        try {
                            connection.sendToUser("\r\nSession Timed Out Administratively");
                        } catch (IOException ex) {
                        }
                        connectionClose(connection);
                    }
                }
            }

        });
         */
        ArrayList<Connection> candidates = new ArrayList<>();

        Iterator<Map.Entry<String, Connection>> iterator = SessionMAP.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Connection> entry = iterator.next();
            Connection connection = entry.getValue();

            // only NEW sessions are involved here
            if (connection.getTerminalSessionInfo().getParent() == null) {
                int max = connection.getTerminalSessionInfo().getMaxIdleTime();
                if (max > 0 && connection.getTerminalSessionInfo().getLastTrafficTime() != 0) {
                    // in seconds
                    max = 60 * max;
                    if ((now - connection.getTerminalSessionInfo().getLastTrafficTime()) >= max) {
                        logger.debug("TIMING out: {}", connection.getTerminalSessionInfo());
                        candidates.add(connection);
                    }
                }
            }
        }
        // GC?
        iterator = null;

        for (Connection connection : candidates) {
            try {
                connection.sendToUser("\r\nSession Timed Out Administratively");
            } catch (IOException ex) {
            }
            connectionClose(connection);
        }
        logger.trace("sessionIdleHouseKeeping end");
    }

    public static Connection tokenToConnection(String token) {
        Iterator<Map.Entry<String, Connection>> iterator = SessionMAP.entrySet().iterator();

        while (token != null && iterator.hasNext()) {
            Map.Entry<String, Connection> entry = iterator.next();
            Connection connection = entry.getValue();
            if (connection.getTerminalSessionInfo().getToken().equals(token)) {
                return connection;
            }
        }

        return null;
    }

    /**
     *
     * @param token
     * @return root level connection
     */
    public static Connection tokenToRootConnection(String token) {
        Iterator<Map.Entry<String, Connection>> iterator = SessionMAP.entrySet().iterator();

        while (token != null && iterator.hasNext()) {
            Map.Entry<String, Connection> entry = iterator.next();
            Connection connection = entry.getValue();
            if (connection.getTerminalSessionInfo().getToken().equals(token)) {
                if (!connection.getTerminalSessionInfo().getSessionType().equalsIgnoreCase(NEW)
                        && connection.getTerminalSessionInfo().getParentToken() != null) {
                    return tokenToRootConnection(connection.getTerminalSessionInfo().getParentToken());
                }

                return connection;
            }
        }

        return null;
    }

    /**
     *
     * @param sessionId
     */
    public static void removeFromSessionMap(String sessionId) {
        SessionMAP.remove(sessionId);
    }

    /*
    public static void dumpSessionMap() {
        SessionMAP.entrySet().forEach((Map.Entry<String, Connection> entry) -> {
            logger.debug("{} -> {}", entry.getKey(), entry.getValue());
        });
    }
     */
    /**
     *
     * @param sessionId
     * @param connection
     */
    public static void addToSessionMap(String sessionId, Connection connection) {
        SessionMAP.put(sessionId, connection);
    }

    /**
     *
     * @param children
     * @param message
     */
    public static void sendToChildren(ArrayList<String> children, TextMessage message) {
        if (children != null) {
            for (String child : children) {
                Connection connection = SessionMAP.get(child);
                if (connection != null) {
                    logger.trace("also send to child: {}", child);
                    try {
                        connection.sendToUser(message);
                    } catch (IOException ex) {
                    }
                }
            }
        }
    }

    /**
     *
     * @param children
     * @param message
     */
    public static void sendToChildren(ArrayList<String> children, BinaryMessage message) {
        if (children != null) {
            for (String child : children) {
                Connection connection = SessionMAP.get(child);
                if (connection != null) {
                    logger.trace("also send to child: {}", child);
                    try {
                        connection.sendToUser(message);
                    } catch (IOException ex) {
                    }
                }
            }
        }
    }

    /**
     *
     * @param parent
     */
    public static void updateParticipantMessage(Connection parent) {
        if (parent != null) {  // could be null
            List<String> children = parent.getTerminalSessionInfo().getChildren();
            if (children != null) {
                String message = "JOINED: " + parent.getTerminalSessionInfo().getWebUserName();
                for (String child : children) {
                    Connection connection = SessionMAP.get(child);
                    if (connection != null) {
                        message += " +" + connection.getTerminalSessionInfo().getWebUserName();
                    }
                }
                TextMessage msg = toClientMessage(message);

                for (String child : children) {
                    Connection connection = SessionMAP.get(child);

                    if (connection != null) {
                        logger.trace("also update to child: {}", child);
                        try {
                            connection.sendToUser(msg);
                        } catch (IOException ex) {
                        }
                    }
                }
                try {
                    parent.sendToUser(msg);
                } catch (IOException ex) {
                }
            }
        }
    }

    /**
     *
     * @param children
     * @param rows
     * @param cols
     */
    public static void sendResizeToChildren(ArrayList<String> children, int rows, int cols) {
        if (children != null) {
            try {
                TextMessage tm = toClientRowCol(rows, cols);

                for (String child : children) {
                    Connection connection = SessionMAP.get(child);
                    if (connection != null) {
                        logger.trace("also send to child: {}", child);
                        try {
                            connection.sendToUser(tm);
                        } catch (IOException ex) {
                        }
                    }
                }
            } catch (JsonProcessingException ex) {
                logger.warn("toClientRowCol JSON serialization error: {}", ex);
            }
        }
    }

    /**
     *
     * @param message
     * @return
     * @throws JsonProcessingException
     */
    public static BinaryMessage toClientBinary(byte[] message) throws JsonProcessingException {
        return new BinaryMessage(message);
    }

    /**
     *
     * @param message
     * @return
     * @throws JsonProcessingException
     */
    public static TextMessage toClientBytes(byte[] message) throws JsonProcessingException {
        // as normal string, instead of encoded           (n) type string
        TwoWayMessage toClient = new TwoWayMessage("n", new String(message));
        String json = objectMapper.writeValueAsString(toClient);
        return new TextMessage(json);

        /*
        // encoded/bigger size, but guaranteed ship over  (e) type encoded
        String messageEncoded = Base64.getEncoder().encodeToString(message);
        TwoWayMessage toClient = new TwoWayMessage("e", messageEncoded);
        String json = objectMapper.writeValueAsString(toClient);
        return new TextMessage(json);
         */
    }

    /**
     *
     * @param message
     * @param type
     * @return
     */
    private static TextMessage toClient(String message, String type) {
        TwoWayMessage toClient = new TwoWayMessage(type, message);
        try {
            String json = objectMapper.writeValueAsString(toClient);
            return new TextMessage(json);
        } catch (JsonProcessingException ex) {
            return new TextMessage(ex.getMessage());
        }
    }

    /**
     *
     * @param message
     * @return
     */
    public static TextMessage toClientString(String message) {
        return toClient(message, "n");
    }

    /**
     *
     * @param message
     * @return
     */
    public static TextMessage toClientMessage(String message) {
        return toClient(message, "m");
    }

    /**
     *
     * @param rows
     * @param cols
     * @return
     * @throws JsonProcessingException
     */
    public static TextMessage toClientRowCol(int rows, int cols) throws JsonProcessingException {
        RowsCols rc = new RowsCols(rows, cols);
        String json = objectMapper.writeValueAsString(rc);
        TwoWayMessage toClient = new TwoWayMessage("s", json);
        json = objectMapper.writeValueAsString(toClient);
        return new TextMessage(json);
    }
}
