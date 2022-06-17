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
import static org.webterminal.constant.Constants.*;
import org.webterminal.pojo.RowsCols;
import org.webterminal.pojo.TerminalSessionInfo;
import static org.webterminal.service.impl.WebTerminalServiceImpl.getRootCause;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;
import org.webterminal.util.AuditLogging;
import static org.webterminal.service.impl.WebTerminalServiceImpl.sendResizeToChildren;
import com.pty4j.WinSize;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.webterminal.util.TimedMatchAndSubmit.matchPromptToSubmit;

public class PtyConnection extends Connection {

    private static final Logger logger = LoggerFactory.getLogger(PtyConnection.class);

    private PtyProcess ptyProcess;

    // ok to use non-atomic among threads, one time check only
    private static boolean telnetOk = false;
    private static boolean sshOk = false;
    private static boolean c3270Ok = false;

    /**
     *
     * @param webSocketSession
     * @param terminalSessionInfo
     * @param auditLogging
     */
    public PtyConnection(WebSocketSession webSocketSession, TerminalSessionInfo terminalSessionInfo, AuditLogging auditLogging) {
        super(webSocketSession, terminalSessionInfo, auditLogging);

        logger.debug("cstor pty Connection to: {}", terminalSessionInfo);
    }

    /**
     *
     * @throws SocketException
     * @throws IOException
     */
    @Override
    public void connect() throws IOException, InterruptedException {
        logger.debug("pty {} to {}:{}", terminalSessionInfo.getConnectionType(), terminalSessionInfo.getHost(), terminalSessionInfo.getPort());

        // check for proper installation on non-Windows
        if (!SystemUtils.IS_OS_WINDOWS) {
            if (terminalSessionInfo.getConnectionType().equalsIgnoreCase(TELNET_LC)) {
                if (!telnetOk) {
                    Path path = Paths.get(TELNET_CMD);
                    if (Files.notExists(path)) {
                        throw new IOException("ConfigError: TELNET/pty NOT SET UP for APP");
                    } else {
                        telnetOk = true;
                    }
                }
            } else if (terminalSessionInfo.getConnectionType().equalsIgnoreCase(SSH_LC)) {
                if (!sshOk) {
                    Path path = Paths.get(SSH_CMD);
                    if (Files.notExists(path)) {
                        throw new IOException("ConfigError: SSH/pty NOT SET UP for APP");
                    } else {
                        sshOk = true;
                    }
                }
            } else {
                if (!c3270Ok) {
                    Path path = Paths.get(C3270_CMD);
                    if (Files.notExists(path)) {
                        throw new IOException("ConfigError: TN3270/pty NOT SET UP for APP");
                    } else {
                        c3270Ok = true;
                    }
                }
            }
        }

        // String[] cmd = {"/bin/sh", "-l"};  // login shell
        // Windows cmd is not really a terminal, so working very limited, only for local testing
        // cmd /c telnet 10.180.36.111 7001
        // Windows telnet is not working properly under cmd when port is specified
        String[] cmd;
        if (terminalSessionInfo.getConnectionType().equalsIgnoreCase(TELNET_LC)) {
            String[] telnetCmd;

            //  -e escapechar
            if (terminalSessionInfo.getPort() != 23) {
                if (SystemUtils.IS_OS_WINDOWS) {
                    String[] winCmd = {"cmd.exe", "/c", "telnet.exe", terminalSessionInfo.getHost(), terminalSessionInfo.getPort().toString()};
                    telnetCmd = winCmd;
                } else {
                    String[] nixCmd = {TELNET_CMD, "-e", "''", terminalSessionInfo.getHost(), terminalSessionInfo.getPort().toString()};
                    telnetCmd = nixCmd;
                }
            } else {
                if (SystemUtils.IS_OS_WINDOWS) {
                    String[] winCmd = {"cmd.exe", "/c", "telnet.exe", terminalSessionInfo.getHost()};
                    telnetCmd = winCmd;
                } else {
                    String[] nixCmd = {TELNET_CMD, "-e", "''", terminalSessionInfo.getHost()};
                    telnetCmd = nixCmd;
                }
            }

            cmd = telnetCmd;
        } else if (terminalSessionInfo.getConnectionType().equalsIgnoreCase(SSH_LC)) {
            // Windows: ssh/putty must be installed for this to work
            //          funky behavior with space character (both telnet/ssh on Windows)
            //          not a concern because deployment will be on Linux
            // less secure options but max compatibility
            String[] sshCmd = {SSH_CMD, "-q",
                "-oUserKnownHostsFile=/dev/null",
                "-oStrictHostKeyChecking=no",
                "-oKexAlgorithms=+diffie-hellman-group1-sha1",
                "-oHostKeyAlgorithms=+ssh-rsa,ssh-dss",
                "-p", terminalSessionInfo.getPort().toString(),
                "-l", terminalSessionInfo.getUsername(),
                terminalSessionInfo.getHost()
            };

            cmd = sshCmd;
        } else {
            // 2>/dev/null
            String[] c3270Cmd = {C3270_CMD, "-secure", terminalSessionInfo.getHost(), terminalSessionInfo.getPort().toString()};

            cmd = c3270Cmd;
        }

        logger.debug("pty command: {}", Arrays.toString(cmd));

        Map<String, String> env = new HashMap<>(System.getenv());

        if (SystemUtils.IS_OS_WINDOWS) {
            env.put("TERM", "vt100");
        } else {
            env.put("TERM", "xterm");
        }

        try {
            ptyProcess = new PtyProcessBuilder(cmd).setEnvironment(env)
                    .setInitialRows(terminalSessionInfo.getRows())
                    .setInitialColumns(terminalSessionInfo.getCols()).start();
        } catch (IOException ex) {
            logger.info("PtyProcessBuilder exception: {}", ex);
            throw new IOException(getRootCause(ex));
        }

        logger.debug("PtyProcessBuilder ok for {}", Arrays.toString(cmd));

        // OutputStream outputStream = ptyProcess.getOutputStream();
        // now work with the stream
        InputStream inputStream = ptyProcess.getInputStream();

        // skip for 3270, only for telnet or ssh
        if (!terminalSessionInfo.getConnectionType().equalsIgnoreCase(TN3270_LC)) {
            boolean telnetAutoUsername = false;
            if (terminalSessionInfo.getConnectionType().equalsIgnoreCase(TELNET_LC)
                    && StringUtils.isNotEmpty(terminalSessionInfo.getUsername())) {
                matchPromptToSubmit(6, inputStream, LOGIN_PROMPTS, terminalSessionInfo.getUsername(), this);
                telnetAutoUsername = true;
            }

            // could be telnet or ssh
            if ((telnetAutoUsername || terminalSessionInfo.getConnectionType().equalsIgnoreCase(SSH_LC))
                    && StringUtils.isNotEmpty(terminalSessionInfo.getPassword())) {
                matchPromptToSubmit(6, inputStream, PASSWORD_PROMPTS, terminalSessionInfo.getPassword(), this);
            }
            // not necessarily logged in as we don't check status after submit, instead turn it over to user
        }

        logger.debug("pty session connected {}", terminalSessionInfo);

        blockingRead(inputStream);
    }

    /**
     *
     * @param command
     * @throws IOException
     */
    @Override
    public void send(String command) throws IOException {
        if (isAlive()) {
            OutputStream outputStream = ptyProcess.getOutputStream();
            outputStream.write(command.getBytes());
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
        return ptyProcess.isAlive();
    }

    /**
     *
     */
    @Override
    public synchronized void close() {
        auditLogging.logClose(terminalSessionInfo);

        if (ptyProcess != null && ptyProcess.isAlive()) {
            try {
                if (ptyProcess.getInputStream() != null) {
                    ptyProcess.getInputStream().close();
                }
                if (ptyProcess.getOutputStream() != null) {
                    ptyProcess.getOutputStream().close();
                }

                logger.debug("Pty close for {}", terminalSessionInfo);
                ptyProcess.destroyForcibly();
                ptyProcess.waitFor();

                logger.debug("after Pty close");

            } catch (InterruptedException | IOException ex) {
                logger.debug("Pty close exception {}", ex);
            }
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
            logger.debug("pty resize {}", rowsCols);

            WinSize ws = new WinSize(rowsCols.getCols(), rowsCols.getRows());
            ptyProcess.setWinSize(ws);

            terminalSessionInfo.setRows(rowsCols.getRows());
            terminalSessionInfo.setCols(rowsCols.getCols());

            // update children's terminal size on their UI
            sendResizeToChildren(terminalSessionInfo.getChildren(),
                    rowsCols.getRows(), rowsCols.getCols());
        }
    }
}
