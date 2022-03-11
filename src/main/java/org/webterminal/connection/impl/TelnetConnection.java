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
import static org.webterminal.constant.Constants.LOGIN_PROMPTS;
import org.webterminal.pojo.RowsCols;
import org.webterminal.pojo.TerminalSessionInfo;
import org.webterminal.util.AuditLogging;
import static org.webterminal.service.impl.WebTerminalServiceImpl.sendResizeToChildren;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.telnet.EchoOptionHandler;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.SuppressGAOptionHandler;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TerminalTypeOptionHandler;
import org.apache.commons.net.telnet.WindowSizeOptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;
import static org.webterminal.constant.Constants.PASSWORD_PROMPTS;
import static org.webterminal.util.TimedMatchAndSubmit.matchPromptToSubmit;

public class TelnetConnection extends Connection {

    private static final Logger logger = LoggerFactory.getLogger(TelnetConnection.class);

    private final TelnetClient telnet;

    /**
     *
     * @param webSocketSession
     * @param terminalSessionInfo
     * @param auditLogging
     */
    public TelnetConnection(WebSocketSession webSocketSession, TerminalSessionInfo terminalSessionInfo, AuditLogging auditLogging) {
        super(webSocketSession, terminalSessionInfo, auditLogging);

        logger.debug("cstor telnet Connection to: {}", terminalSessionInfo);

        this.telnet = new TelnetClient();
    }

    /**
     *
     * @throws SocketException
     * @throws IOException
     */
    @Override
    public void connect() throws IOException, InterruptedException {
        logger.debug("telnet to {}:{}", terminalSessionInfo.getHost(), terminalSessionInfo.getPort());

        // These options need tweak, not working unless the other side is well behaved
        TerminalTypeOptionHandler ttopt = new TerminalTypeOptionHandler("xterm", true, true, false, false);
        EchoOptionHandler echoopt = new EchoOptionHandler(false, false, true, true);
        SuppressGAOptionHandler gaopt = new SuppressGAOptionHandler(false, false, true, true);

        // width/height
        WindowSizeOptionHandler wsoh = new WindowSizeOptionHandler(
                terminalSessionInfo.getCols(), terminalSessionInfo.getRows(),
                true, true, true, true);

        try {
            telnet.addOptionHandler(ttopt);
            telnet.addOptionHandler(echoopt);
            telnet.addOptionHandler(gaopt);
            telnet.addOptionHandler(wsoh);
        } catch (InvalidTelnetOptionException e) {
            logger.error("Error registering telnet option handlers: {}", e.getMessage());
            throw new IOException(e.getMessage());
        }

        telnet.setConnectTimeout(10000);
        telnet.connect(terminalSessionInfo.getHost(), terminalSessionInfo.getPort());
        telnet.setKeepAlive(true);
        logger.debug("telnet connection established");

        InputStream inputStream = telnet.getInputStream();

        // auto login iff both set:  terminalSessionInfo.getUsername() / terminalSessionInfo.getPassword()
        // not going to handle fancy interactions (like password expired, etc)
        if (StringUtils.isNotBlank(terminalSessionInfo.getUsername()) && StringUtils.isNotBlank(terminalSessionInfo.getPassword())) {
            logger.debug("attempt auto login for {}", terminalSessionInfo);

            try {
                matchPromptToSubmit(6, inputStream, LOGIN_PROMPTS, terminalSessionInfo.getUsername(), this);
                matchPromptToSubmit(6, inputStream, PASSWORD_PROMPTS, terminalSessionInfo.getPassword(), this);
            } catch (IOException | InterruptedException ex) {
                sendToUser("Auto Login Failed: " + ex.getMessage());

                throw (ex);
            }
            logger.debug("finished auto login sequence for {}", terminalSessionInfo);
            // not necessarily logged in as we don't check status after submit, instead turn it over to user
        }

        logger.debug("telnet session connected {}", terminalSessionInfo);

        blockingRead(inputStream);
    }

    /**
     *
     * @param data
     * @throws IOException
     */
    @Override
    public void send(String data) throws IOException {
        if (isAlive()) {
            OutputStream outputStream = telnet.getOutputStream();
            outputStream.write(data.getBytes());
            outputStream.flush();

            terminalSessionInfo.setTrafficTimeNow();
        } else {
            throw new IOException("no valid backend connection");
        }
    }

    /**
     *
     * @return
     */
    @Override
    public boolean isAlive() {
        return telnet.isConnected() && telnet.isAvailable();
    }

    /**
     *
     */
    @Override
    public synchronized void close() {
        auditLogging.logClose(terminalSessionInfo);

        try {
            telnet.disconnect();

            if (telnet.getInputStream() != null) {
                telnet.getInputStream().close();
            }
            if (telnet.getOutputStream() != null) {
                telnet.getOutputStream().close();
            }
        } catch (IOException ex) {
        }
    }

    /**
     *
     * @param rowsCols
     */
    @Override
    public void resize(RowsCols rowsCols) {
        if (rowsCols.getRows() != terminalSessionInfo.getRows()
                || rowsCols.getCols() != terminalSessionInfo.getCols()) {
            logger.debug("telnet resize {}", rowsCols);

            WindowSizeOptionHandler wsoh = new WindowSizeOptionHandler(
                    rowsCols.getCols(), rowsCols.getRows(),
                    true, true, true, true);
            try {
                //telnetConnectInfo.getTelnet().deleteOptionHandler(TelnetOption.WINDOW_SIZE);
                //telnetConnectInfo.getTelnet().addOptionHandler(wsoh);

                telnet.sendSubnegotiation(wsoh.startSubnegotiationLocal());
            } catch (IOException ex) {
                logger.debug("resize ex: {}", ex);
            }

            terminalSessionInfo.setRows(rowsCols.getRows());
            terminalSessionInfo.setCols(rowsCols.getCols());

            // update children's terminal size on their UI
            sendResizeToChildren(terminalSessionInfo.getChildren(),
                    rowsCols.getRows(), rowsCols.getCols());
        }
    }
}
