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

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.webterminal.connection.Connection;
import org.webterminal.pojo.RowsCols;
import org.webterminal.pojo.TerminalSessionInfo;
import org.webterminal.util.AuditLogging;
import static org.webterminal.service.impl.WebTerminalServiceImpl.sendResizeToChildren;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

public class SshConnection extends Connection {

    private static final Logger logger = LoggerFactory.getLogger(SshConnection.class);

    private final JSch jSch;
    private Channel channel;
    private Session session;

    /**
     *
     * @param webSocketSession
     * @param terminalSessionInfo
     * @param auditLogging
     */
    public SshConnection(WebSocketSession webSocketSession, TerminalSessionInfo terminalSessionInfo, AuditLogging auditLogging) {
        super(webSocketSession, terminalSessionInfo, auditLogging);

        logger.debug("cstor ssh connection to: {}", terminalSessionInfo);

        this.jSch = new JSch();
    }

    /**
     *
     * @throws JSchException
     * @throws SocketException
     * @throws IOException
     */
    @Override
    public void connect() throws JSchException, IOException {
        logger.debug("ssh to {}:{}", terminalSessionInfo.getHost(), terminalSessionInfo.getPort());

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session = jSch.getSession(terminalSessionInfo.getUsername(), terminalSessionInfo.getHost(), terminalSessionInfo.getPort());
        session.setConfig(config);
        session.setPassword(terminalSessionInfo.getPassword());
        session.connect(10000);

        channel = session.openChannel("shell");
        ((ChannelShell) channel).setPty(true);
        ((ChannelShell) channel).setPtyType("xterm",
                terminalSessionInfo.getCols(), terminalSessionInfo.getRows(), 0, 0);

        channel.connect(5000);

        InputStream inputStream = channel.getInputStream();

        logger.debug("ssh session connected {}", terminalSessionInfo);

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
            OutputStream outputStream = channel.getOutputStream();
            outputStream.write(data.getBytes(StandardCharsets.UTF_8));
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
        return channel.isConnected();
    }

    /**
     *
     */
    @Override
    public synchronized void close() {
        auditLogging.logClose(terminalSessionInfo);

        try {
            if (channel != null
                    && channel.getInputStream() != null) {
                channel.getInputStream().close();
            }
        } catch (IOException ex) {
        }
        if (channel != null) {
            channel.disconnect();
        }

        if (session != null) {
            session.disconnect();
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
            logger.debug("ssh resize {}", rowsCols);

            ((ChannelShell) channel).
                    setPtySize(rowsCols.getCols(), rowsCols.getRows(), 0, 0);

            terminalSessionInfo.setRows(rowsCols.getRows());
            terminalSessionInfo.setCols(rowsCols.getCols());

            // update children's terminal size on their UI
            sendResizeToChildren(terminalSessionInfo.getChildren(),
                    rowsCols.getRows(), rowsCols.getCols());
        }
    }
}
