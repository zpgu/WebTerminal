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
package org.webterminal.util;

import org.webterminal.pojo.TerminalSessionInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import static java.lang.System.arraycopy;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLoggingToFile implements AuditLogging {

    private static final Logger logger = LoggerFactory.getLogger(AuditLoggingToFile.class);

    private final String auditLogDir;

    private final int bufsize;

    private final ArrayList<byte[]> bufferedChain = new ArrayList<>();
    private int bufferedLength = 0;

    /**
     *
     * @param auditLogDir
     */
    public AuditLoggingToFile(String auditLogDir) {
        this.auditLogDir = auditLogDir;
        bufsize = 24 * 80;
    }

    /**
     *
     * @param auditLogDir
     * @param bufsize
     */
    public AuditLoggingToFile(String auditLogDir, int bufsize) {
        this.auditLogDir = auditLogDir;
        this.bufsize = bufsize;
    }

    private void saveToChain(byte[] data) {
        bufferedChain.add(data);
        bufferedLength += data.length;

        while (bufferedLength > bufsize && bufferedChain.size() > 1) {
            byte[] popped = bufferedChain.remove(0);
            bufferedLength -= popped.length;
        }
    }

    /**
     *
     * @return
     */
    @Override
    public byte[] bufferedData() {
        byte[] combo = new byte[bufferedLength];
        int at = 0;
        for (int i = 0; i < bufferedChain.size(); i++) {
            byte[] one = bufferedChain.get(i);
            arraycopy(one, 0, combo, at, one.length);
            at += one.length;
        }

        return combo;
    }

    /**
     *
     * @param terminalSessionInfo
     * @param data
     */
    @Override
    public void logTraffic(TerminalSessionInfo terminalSessionInfo, byte[] data) {
        saveToChain(data);

        if (terminalSessionInfo.getAuditLogging().equals("ON")) {
            if (terminalSessionInfo.getLogWriter() == null) {
                Path path = Paths.get(auditLogDir + "/" + terminalSessionInfo.getLogFileName() + ".log");
                if (Files.notExists(path)) {
                    try {
                        Files.createFile(path);
                    } catch (IOException ex) {
                        logger.warn("logTraffic failed to create file {}: {}", path.getFileName(), ex.getMessage());
                    }
                }

                try {
                    BufferedWriter bw = Files.newBufferedWriter(
                            path,
                            Charset.defaultCharset(),
                            StandardOpenOption.APPEND);
                    terminalSessionInfo.setLogWriter(bw);
                } catch (IOException ex) {
                    logger.warn("logTraffic failed to create BufferedWriter: {}", ex.getMessage());
                }
            }

            if (terminalSessionInfo.getLogWriter() != null) {
                try {
                    terminalSessionInfo.getLogWriter().write(new String(data));
                } catch (IOException ex) {
                    logger.warn("logTraffic failed to write: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     *
     * @param terminalSessionInfo
     */
    @Override
    public void logClose(TerminalSessionInfo terminalSessionInfo
    ) {
        if (terminalSessionInfo.getLogWriter() != null) {
            try {
                terminalSessionInfo.getLogWriter().close();
            } catch (IOException ex) {
                logger.info("logClose exception: {}", ex.getMessage());
            }
        }
    }
}
