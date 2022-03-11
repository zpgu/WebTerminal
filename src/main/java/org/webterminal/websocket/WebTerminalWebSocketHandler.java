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
package org.webterminal.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import static org.webterminal.constant.Constants.SESSION_UUID;
import org.webterminal.service.WebTerminalService;
import static org.webterminal.service.impl.WebTerminalServiceImpl.sessionClose;

@Component
public class WebTerminalWebSocketHandler implements WebSocketHandler {

    @Autowired
    private WebTerminalService webTerminalService;
    private final Logger logger = LoggerFactory.getLogger(WebTerminalWebSocketHandler.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
        webTerminalService.initSession(webSocketSession);
    }

    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> webSocketMessage) throws Exception {
        if (webSocketMessage instanceof TextMessage) {
            //logger.debug("Session: {}, command: {}", webSocketSession.getAttributes().get(SESSION_UUID), webSocketMessage.toString());
            logger.trace("Session: {}, payload: {}", webSocketSession.getAttributes().get(SESSION_UUID), webSocketMessage.getPayload());
            webTerminalService.textMessageHandler(((TextMessage) webSocketMessage).getPayload(), webSocketSession);
        } else if (webSocketMessage instanceof BinaryMessage) {
            logger.warn("NotHandled Binary WebSocket message: {}", webSocketMessage);
        } else if (webSocketMessage instanceof PingMessage) {
            logger.warn("NotHandled Ping WebSocket message: {}", webSocketMessage);
        } else if (webSocketMessage instanceof PongMessage) {
            logger.warn("NotHandled Pong WebSocket message: {}", webSocketMessage);
        } else {
            logger.warn("Unexpected WebSocket message: {}", webSocketMessage);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable throwable) throws Exception {
        logger.error("Data transport error: ", throwable.toString());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus closeStatus) throws Exception {
        logger.debug("Session: {} disconnect WebTerminal", String.valueOf(webSocketSession.getAttributes().get(SESSION_UUID)));
        sessionClose(webSocketSession);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
