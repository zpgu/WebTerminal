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

public class TokenRowsCols {

    String token;
    int rows;
    int cols;

    /**
     *
     */
    public TokenRowsCols() {
    }

    /**
     *
     * @param rows
     * @param cols
     */
    public TokenRowsCols(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }

    /**
     *
     * @return
     */
    public String getToken() {
        return token;
    }

    /**
     *
     * @param token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     *
     * @return
     */
    public int getRows() {
        return rows;
    }

    /**
     *
     * @param rows
     */
    public void setRows(int rows) {
        this.rows = rows;
    }

    /**
     *
     * @return
     */
    public int getCols() {
        return cols;
    }

    /**
     *
     * @param cols
     */
    public void setCols(int cols) {
        this.cols = cols;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
