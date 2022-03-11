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

import static org.webterminal.constant.Constants.FONTSIZE;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

public class TokenRequest {

    private String sessionType;           //  NEW/JOIN/TAKE/WATCH, default NEW
    private String parentToken;           //  needed if JOIN/WATCH/TAKE

    private String description;
    private String host;
    private Integer port;
    private String connectionType;        // ssh/telnet/tn3270
    private boolean usePty = false;
    private String username;
    private String password;
    private String webUserName;
    private String webUserRole;
    private String webUserIp;
    private Integer maxIdleTime = 5;      // in minutes
    private String auditLogging = "OFF";  // OFF/ON; should be boolean?
    private Integer fontSize = FONTSIZE;
    private boolean visibleToAll = false; // session visiblet to all

    // used on server side only to track age of request
    private String inTime;

    // TODO: some of the fields from old setup???
    /*
    private String id;
    private String uiUser;
    private Integer timeout;
    private String destinationLabel;
    private String portLabel;
    private Integer nodeId;
    private Boolean localEcho;
     */
    /**
     *
     * @return
     */
    public boolean isVisibleToAll() {
        return visibleToAll;
    }

    /**
     *
     * @param visibleToAll
     */
    public void setVisibleToAll(boolean visibleToAll) {
        this.visibleToAll = visibleToAll;
    }

    /**
     *
     * @return
     */
    public String getWebUserRole() {
        return webUserRole;
    }

    /**
     *
     * @param webUserRole
     */
    public void setWebUserRole(String webUserRole) {
        this.webUserRole = webUserRole;
    }

    /**
     *
     * @return
     */
    public Integer getFontSize() {
        return fontSize;
    }

    /**
     *
     * @param fontSize
     */
    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }

    /**
     *
     * @return
     */
    public String getSessionType() {
        return sessionType;
    }

    /**
     *
     * @param sessionType
     */
    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    /**
     *
     * @return
     */
    public String getParentToken() {
        return parentToken;
    }

    /**
     *
     * @param parentToken
     */
    public void setParentToken(String parentToken) {
        this.parentToken = parentToken;
    }

    /**
     *
     * @return
     */
    public String getAuditLogging() {
        return auditLogging;
    }

    /**
     *
     * @param auditLogging
     */
    public void setAuditLogging(String auditLogging) {
        this.auditLogging = auditLogging;
    }

    /**
     *
     * @return
     */
    public Integer getMaxIdleTime() {
        return maxIdleTime;
    }

    /**
     *
     * @param maxIdleTime
     */
    public void setMaxIdleTime(Integer maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    /**
     *
     * @return
     */
    public boolean isUsePty() {
        return usePty;
    }

    /**
     *
     * @param usePty
     */
    public void setUsePty(boolean usePty) {
        this.usePty = usePty;
    }

    /**
     *
     * @return
     */
    public String getWebUserName() {
        return webUserName;
    }

    /**
     *
     * @param webUserName
     */
    public void setWebUserName(String webUserName) {
        this.webUserName = webUserName;
    }

    /**
     *
     * @return
     */
    public String getWebUserIp() {
        return webUserIp;
    }

    /**
     *
     * @param webUserIp
     */
    public void setWebUserIp(String webUserIp) {
        this.webUserIp = webUserIp;
    }

    /**
     *
     * @return
     */
    public String getInTime() {
        return inTime;
    }

    /**
     *
     * @param inTime
     */
    public void setInTime(String inTime) {
        this.inTime = inTime;
    }

    /**
     *
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     *
     * @param description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     *
     * @return
     */
    public String getHost() {
        return host;
    }

    /**
     *
     * @param host
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     *
     * @return
     */
    public Integer getPort() {
        return port;
    }

    /**
     *
     * @param port
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     *
     * @return
     */
    public String getUsername() {
        return username;
    }

    /**
     *
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     *
     * @return
     */
    public String getPassword() {
        return password;
    }

    /**
     *
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     *
     * @return
     */
    public String getConnectionType() {
        return connectionType;
    }

    /**
     *
     * @param connectionType
     */
    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toStringExclude(this, "password");
    }
}
