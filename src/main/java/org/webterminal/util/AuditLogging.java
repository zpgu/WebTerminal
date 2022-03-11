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

public interface AuditLogging {

    /**
     * logs traffic data for session of terminalSessionInfo
     *
     * @param terminalSessionInfo
     * @param data
     */
    public void logTraffic(
            TerminalSessionInfo terminalSessionInfo,
            byte[] data);

    /**
     * close log
     *
     * @param terminalSessionInfo
     */
    public void logClose(TerminalSessionInfo terminalSessionInfo);

    /**
     * returns buffered data to send to joined sessions
     *
     * @return
     */
    public byte[] bufferedData();
}
