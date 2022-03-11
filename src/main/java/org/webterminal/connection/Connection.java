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
package org.webterminal.connection;

import com.jcraft.jsch.JSchException;
import org.webterminal.pojo.RowsCols;
import org.webterminal.pojo.TerminalSessionInfo;
import org.webterminal.service.impl.WebTerminalServiceImpl;
import static org.webterminal.service.impl.WebTerminalServiceImpl.disconnectChildren;
import static org.webterminal.service.impl.WebTerminalServiceImpl.toClientBytes;
import static org.webterminal.service.impl.WebTerminalServiceImpl.toClientString;
import org.webterminal.util.AuditLogging;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Arrays;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import static org.webterminal.service.impl.WebTerminalServiceImpl.connectionClose;

public abstract class Connection {

    /**
     * webSocketSession of this connection, potentially switched out
     */
    protected volatile WebSocketSession webSocketSession;

    /**
     * terminalSessionInfo of this connection
     */
    protected final TerminalSessionInfo terminalSessionInfo;

    /**
     * auditLogging of this connection
     */
    protected final AuditLogging auditLogging;

    /**
     * construct/init Connection
     *
     * @param webSocketSession
     * @param terminalSessionInfo
     * @param auditLogging
     */
    public Connection(WebSocketSession webSocketSession, TerminalSessionInfo terminalSessionInfo, AuditLogging auditLogging) {
        this.terminalSessionInfo = terminalSessionInfo;
        this.auditLogging = auditLogging;
        this.webSocketSession = webSocketSession;
    }

    /**
     * starts actual connection (in a separate thread)
     *
     * @throws JSchException
     * @throws SocketException
     * @throws IOException
     * @throws java.lang.InterruptedException
     */
    public abstract void connect() throws JSchException, IOException, InterruptedException;

    /**
     * send data to downstream
     *
     * @param data
     * @throws IOException
     */
    public abstract void send(String data) throws IOException;

    /**
     *
     * @return Boolean
     */
    public abstract boolean isAlive();

    /**
     * send message to upstream user (UI)
     *
     * @param message
     * @throws IOException
     */
    public void sendToUser(byte[] message) throws IOException {
        // tm is lot larger than bm, but client side processing for bm takes longer
        //
        //TextMessage tm = toClientBytes(message);
        //BinaryMessage bm = toClientBinary(message);
        //logger.debug("tm: {}, bm: {}", tm.getPayloadLength(), bm.getPayloadLength());

        this.sendToUser(toClientBytes(message));
        //this.sendToUser(toClientBinary(message));
    }

    /**
     * send message to upstream user (UI)
     *
     * @param message
     * @throws IOException
     */
    public void sendToUser(String message) throws IOException {
        this.sendToUser(toClientString(message));
    }

    /**
     * send message to upstream user (UI)
     *
     * @param message
     * @throws IOException
     */
    public void sendToUser(TextMessage message) throws IOException {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            // need ordered access to webSocketSession, ok to sync on non-final here
            synchronized (webSocketSession) {
                webSocketSession.sendMessage(message);
            }
            terminalSessionInfo.setTrafficTimeNow();
        }
        // need to send to child sessions if any
        WebTerminalServiceImpl.sendToChildren(terminalSessionInfo.getChildren(), message);
    }

    /**
     * send message to upstream user (UI)
     *
     * @param message
     * @throws IOException
     */
    public void sendToUser(BinaryMessage message) throws IOException {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            // ok to synch on non-final here
            synchronized (webSocketSession) {
                webSocketSession.sendMessage(message);
            }

            terminalSessionInfo.setTrafficTimeNow();
        }
        // need to send to child sessions if any
        WebTerminalServiceImpl.sendToChildren(terminalSessionInfo.getChildren(), message);
    }

    /**
     *
     * @return
     */
    public TerminalSessionInfo getTerminalSessionInfo() {
        return terminalSessionInfo;
    }

    /**
     *
     * @return
     */
    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    /**
     *
     * @param webSocketSession
     */
    public void setWebSocketSession(WebSocketSession webSocketSession) {
        this.webSocketSession = webSocketSession;
    }

    /**
     * close webSocketSession only
     */
    public void webSocketSessionClose() {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                webSocketSession.close();
            } catch (IOException ex) {
            }
        }
    }

    /**
     * suspends connection
     */
    public void suspend() {
        disconnectChildren(terminalSessionInfo);
        terminalSessionInfo.setSuspended(true);

        try {
            sendToUser("\r\nSession Suspended");
            webSocketSession.close();
        } catch (IOException ex) {
        }

        webSocketSession = null;
    }

    /**
     *
     * @return
     */
    public boolean isSuspended() {
        return terminalSessionInfo.isSuspended();
    }

    /**
     * close the other side connection (not webSocketSession side)
     */
    public abstract void close();

    /**
     * gets buffered data
     *
     * @return buffered data as byte[]
     */
    public byte[] getBufferedData() {
        return auditLogging.bufferedData();
    }

    /**
     * act upon received screen size change
     *
     * @param rowCols
     */
    public abstract void resize(RowsCols rowCols);

    /**
     *
     * @param inputStream
     * @throws IOException
     */
    public void blockingRead(InputStream inputStream) throws IOException {
        sendToUser("Connection ready\r\n");

        // wipe password now
        if (terminalSessionInfo.getPassword() != null) {
            terminalSessionInfo.setPassword("*");
        }
        terminalSessionInfo.setReady(true);

        try {
            byte[] buffer = new byte[1024];
            int i;
            while ((i = inputStream.read(buffer)) != -1) {
                byte[] bi = Arrays.copyOfRange(buffer, 0, i);
                sendToUser(bi);
                terminalSessionInfo.setTrafficTimeNow();
                auditLogging.logTraffic(terminalSessionInfo, bi);
            }
        } finally {
            sendToUser("Server Closed Connection");
            connectionClose(this);
        }
    }
}
