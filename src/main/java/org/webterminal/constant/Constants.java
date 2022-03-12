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
package org.webterminal.constant;

public class Constants {

    /**
     *
     */
    public static final String SESSION_UUID = "SessionId";

    /**
     *
     */
    public static final String CLIENT_CONNECT = "connect";

    /**
     *
     */
    public static final String CLIENT_RESIZE = "resize";

    /**
     *
     */
    public static final String CLIENT_SUSPEND = "suspend";

    /**
     *
     */
    public static final String CLIENT_DISCONNECT = "disconnect";

    /**
     * save a few bytes (instead of "data"), as it's frequently used
     */
    public static final String CLIENT_DATA = "d";  // save a few bytes for client

    /**
     * potential prompt ending, must be lower case, ends with 1 space
     */
    public static final String[] LOGIN_PROMPTS = {"login: ", "user: ", "name: ", "id: "};

    /**
     *
     */
    public static final String[] PASSWORD_PROMPTS = {"password: "};

    /**
     *
     */
    public static final String CR_LF = "\r\n";

    /**
     *
     */
    public static final String LF = "\n";

    /**
     *
     */
    public static final String SSH_LC = "ssh";

    /**
     *
     */
    public static final String TELNET_LC = "telnet";

    /**
     *
     */
    public static final String TN3270_LC = "tn3270";

    /**
     *
     */
    public static final String NEW = "NEW";

    /**
     *
     */
    public static final String JOIN = "JOIN";

    /**
     *
     */
    public static final String WATCH = "WATCH";

    /**
     *
     */
    public static final String TAKE = "TAKE";

    /**
     *
     */
    public static final int FONTSIZE = 15;

    /**
     * for pty telnet on Linux at least, won't work on Windows
     */
    public static final String TELNET_CMD = "/usr/bin/telnet";

    /**
     * for pty c3270 on Linux at least, won't work on Windows
     */
    public static final String C3270_CMD = "/usr/bin/c3270";

    /**
     * for pty ssh on Linux at least, won't work on Windows
     */
    public static final String SSH_CMD = "/usr/bin/ssh";
}
