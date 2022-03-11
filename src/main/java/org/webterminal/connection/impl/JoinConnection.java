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
package org.webterminal.connection.impl;

import org.webterminal.connection.Connection;
import static org.webterminal.constant.Constants.JOIN;
import static org.webterminal.constant.Constants.TAKE;
import static org.webterminal.constant.Constants.WATCH;
import org.webterminal.pojo.RowsCols;
import org.webterminal.pojo.TerminalSessionInfo;
import org.webterminal.util.AuditLogging;
import static org.webterminal.service.impl.WebTerminalServiceImpl.addToSessionMap;
import static org.webterminal.service.impl.WebTerminalServiceImpl.toClientString;
import static org.webterminal.service.impl.WebTerminalServiceImpl.tokenToRootConnection;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;
import static org.webterminal.service.impl.WebTerminalServiceImpl.connectionClose;
import static org.webterminal.service.impl.WebTerminalServiceImpl.updateParticipantMessage;
import static org.webterminal.service.impl.WebTerminalServiceImpl.removeFromSessionMap;

public class JoinConnection extends Connection {

    private static final Logger logger = LoggerFactory.getLogger(JoinConnection.class);

    private final Connection parentConnection;

    /**
     *
     * @param webSocketSession
     * @param terminalSessionInfo
     * @param auditLogging
     */
    public JoinConnection(WebSocketSession webSocketSession, TerminalSessionInfo terminalSessionInfo, AuditLogging auditLogging) {
        super(webSocketSession, terminalSessionInfo, auditLogging);

        logger.debug("cstor Join connection: {}", terminalSessionInfo);

        // could be null
        this.parentConnection = tokenToRootConnection(terminalSessionInfo.getParentToken());
    }

    /**
     *
     * @throws IOException
     */
    @Override
    public void connect() throws IOException {
        if (parentConnection != null && parentConnection.isAlive()) {
            TerminalSessionInfo parentTerminalSessionInfo = parentConnection.getTerminalSessionInfo();

            terminalSessionInfo.setHost(parentTerminalSessionInfo.getHost());
            terminalSessionInfo.setPort(parentTerminalSessionInfo.getPort());
            terminalSessionInfo.setConnectionType(parentTerminalSessionInfo.getConnectionType());

            if (parentConnection.getTerminalSessionInfo().isSuspended()
                    || terminalSessionInfo.getSessionType().equals(TAKE)) {
                logger.debug("TAKE over parent: {}", parentTerminalSessionInfo);

                parentConnection.sendToUser(toClientString("\r\nSession taken by " + terminalSessionInfo.getWebUserName() + "\r\n"));

                WebSocketSession parentWss = parentConnection.getWebSocketSession();
                String parentSessionId = parentConnection.getTerminalSessionInfo().getSessionId();

                // switch in wss, token, sessionId into parentTerminalSessionInfo
                parentConnection.setWebSocketSession(webSocketSession);
                parentConnection.getTerminalSessionInfo().setToken(terminalSessionInfo.getToken());
                parentConnection.getTerminalSessionInfo().setSessionId(terminalSessionInfo.getSessionId());

                parentConnection.getTerminalSessionInfo().setSuspended(false);

                // switch in UI side info
                parentConnection.getTerminalSessionInfo().setWebUserIp(terminalSessionInfo.getWebUserIp());
                parentConnection.getTerminalSessionInfo().setWebUserName(terminalSessionInfo.getWebUserName());
                parentConnection.getTerminalSessionInfo().setWebUserRole(terminalSessionInfo.getWebUserRole());

                // switch in connection
                addToSessionMap(terminalSessionInfo.getSessionId(), parentConnection);
                // remove original parent connection mapping
                removeFromSessionMap(parentSessionId);

                parentConnection.resize(new RowsCols(terminalSessionInfo.getRows(), terminalSessionInfo.getCols()));

                // only disconnect/close parentWss after above setup done
                if (parentWss != null && parentWss.isOpen()) {
                    try {
                        parentWss.close();
                    } catch (IOException ex) {
                    }
                }

                parentConnection.getTerminalSessionInfo().setReady(true);

                parentConnection.sendToUser(parentConnection.getBufferedData());
            } else {
                terminalSessionInfo.setParent(parentTerminalSessionInfo);
                logger.debug("join to parent {}", parentTerminalSessionInfo);

                // inform parentTerminalSessionInfo
                parentTerminalSessionInfo.addChild(terminalSessionInfo.getSessionId());

                // tell everyone who is on the session
                updateParticipantMessage(parentConnection);

                terminalSessionInfo.setReady(true);

                if (terminalSessionInfo.getSessionType().equals(JOIN)) {
                    sendToUser("Join Session ready\r\n");
                } else if (terminalSessionInfo.getSessionType().equals(WATCH)) {
                    sendToUser("ReadOnly Session attached\r\n");
                }

                sendToUser(parentConnection.getBufferedData());
            }

            logger.debug("child session connected {}", terminalSessionInfo);

        } else {
            if (parentConnection != null) {
                connectionClose(parentConnection);
            }

            sendToUser("Session No Longer Available");
            connectionClose(this);
        }
    }

    /**
     *
     * @param data
     * @throws IOException
     */
    @Override
    public void send(String data) throws IOException {
        // only if JOIN
        if (terminalSessionInfo.getSessionType().equals(JOIN) && parentConnection != null) {
            parentConnection.send(data);
        } else {
            logger.debug("user input ignored when session is WATCH only");
        }
    }

    @Override
    public boolean isAlive() {
        return parentConnection.isAlive();
    }

    /**
     *
     */
    @Override
    public synchronized void close() {
        logger.debug("join close() {}", terminalSessionInfo);

        auditLogging.logClose(terminalSessionInfo);

        if (parentConnection != null) {
            parentConnection.getTerminalSessionInfo().removeChild(terminalSessionInfo.getSessionId());
        }
        removeFromSessionMap(terminalSessionInfo.getSessionId());

        // update everyone who is still on
        updateParticipantMessage(parentConnection);
    }

    /**
     *
     * @param webSocketSession
     */
    public void suspend(WebSocketSession webSocketSession) {
        logger.error("can't suspend secondary connection: {}", terminalSessionInfo);
    }

    /**
     *
     * @param rowsCols
     */
    @Override
    public void resize(RowsCols rowsCols) {
        logger.error("ignore join resize {}", rowsCols);
    }
}
