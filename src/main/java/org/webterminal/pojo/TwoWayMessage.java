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
package org.webterminal.pojo;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

public class TwoWayMessage {

    // FROM SERVER TO CLIENT
    // type     e/n/s/m   encoded/string/size/joinedUserList
    // payload  string    encoded data/string/json of ROWCOL/string
    //
    // FROM CLIENT TO SERVER
    // type     connect/resize/suspend
    // payload  TokenRowsCols/RowCol/Token
    //
    private String t; // type
    private String p; // payload

    /**
     *
     * @param t
     * @param p
     */
    public TwoWayMessage(String t, String p) {
        this.t = t;
        this.p = p;
    }

    /**
     *
     */
    public TwoWayMessage() {
    }

    /**
     *
     * @return
     */
    public String getT() {
        return t;
    }

    /**
     *
     * @param t
     */
    public void setT(String t) {
        this.t = t;
    }

    /**
     *
     * @return
     */
    public String getP() {
        return p;
    }

    /**
     *
     * @param p
     */
    public void setP(String p) {
        this.p = p;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
