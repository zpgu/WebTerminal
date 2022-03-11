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

import com.fasterxml.jackson.annotation.JsonIgnore;
import static org.webterminal.constant.Constants.JOIN;
import static org.webterminal.constant.Constants.NEW;
import static org.webterminal.constant.Constants.SSH_LC;
import static org.webterminal.constant.Constants.WATCH;
import static org.webterminal.constant.Constants.TAKE;
import static org.webterminal.constant.Constants.TN3270_LC;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// more than a pojo, but ...
public class TerminalSessionInfo {

    private boolean suspended = false;

    private String sessionType;          //   NEW/JOIN/TAKE/WATCH, default NEW
    private String token;
    private String parentToken;

    private String description;
    private String host;
    private Integer port;
    private String connectionType;
    private boolean usePty;
    private String username;
    private String password;
    private String webUserName;
    private String webUserRole;
    private String webUserIp;
    private Integer maxIdleTime;
    private String auditLogging;
    private Integer fontSize;
    private boolean visibleToAll;

    private String inTime;

    private volatile boolean ready;
    private String sessionId;
    private long lastTrafficTime = 0;   // to track idle timeout

    private int rows = 24;
    private int cols = 80;

    // to track related sessions for JOIN/WATCH
    private TerminalSessionInfo parent;
    // sessionId list of children
    private ArrayList<String> children;

    @JsonIgnore
    private BufferedWriter logWriter;
    @JsonIgnore
    private final Lock lock = new ReentrantLock();
    @JsonIgnore
    private static final Logger logger = LoggerFactory.getLogger(TerminalSessionInfo.class);

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
    public boolean isSuspended() {
        return suspended;
    }

    /**
     *
     * @param suspended
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
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
    public String getSessionId() {
        return sessionId;
    }

    /**
     *
     * @param sessionId
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     *
     * @param child
     */
    public void addChild(String child) {
        lock.lock();
        try {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(child);
        } finally {
            lock.unlock();
        }
    }

    /**
     *
     * @param child
     */
    public void removeChild(String child) {
        lock.lock();
        try {
            if (children != null) {
                children.remove(child);
            }
        } finally {
            lock.unlock();
        }
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
    public TerminalSessionInfo getParent() {
        return parent;
    }

    /**
     *
     * @param parent
     */
    public void setParent(TerminalSessionInfo parent) {
        this.parent = parent;
    }

    /**
     *
     * @return
     */
    public ArrayList<String> getChildren() {
        return children;
    }

    /**
     *
     * @param children
     */
    public void setChildren(ArrayList<String> children) {
        lock.lock();
        try {
            this.children = children;
        } finally {
            lock.unlock();
        }
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
    @JsonIgnore
    public BufferedWriter getLogWriter() {
        return logWriter;
    }

    /**
     *
     * @param logWriter
     */
    public void setLogWriter(BufferedWriter logWriter) {
        this.logWriter = logWriter;
    }

    /**
     *
     * @return
     */
    @JsonIgnore
    public String getLogFileName() {
        String logFileName = connectionType + "_" + host + "_" + port + "_"
                + description + "_" + webUserName + "@" + webUserIp + "_" + inTime;
        // replace ':' with '_'
        logFileName = logFileName.replace(':', '_');

        return logFileName;
    }

    /**
     *
     * @return
     */
    public boolean isReady() {
        return ready;
    }

    /**
     *
     * @param ready
     */
    public void setReady(boolean ready) {
        this.ready = ready;
        this.setTrafficTimeNow();
    }

    /**
     *
     * @return
     */
    public long getLastTrafficTime() {
        return lastTrafficTime;
    }

    /**
     *
     * @param lastTrafficTime
     */
    public void setLastTrafficTime(long lastTrafficTime) {
        this.lastTrafficTime = lastTrafficTime;
    }

    /**
     *
     */
    public void setTrafficTimeNow() {
        if (this.ready) {
            this.lastTrafficTime = System.currentTimeMillis() / 1000l;
        }
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

    /**
     *
     * @param req
     */
    public void transferFromTokenRequest(TokenRequest req) {
        this.setFontSize(req.getFontSize());

        // terminalSessionInfo.inTime is session in time
        DateTimeFormatter parser = ISODateTimeFormat.dateTimeNoMillis();
        String tstr = new DateTime().toString(parser);
        this.setInTime(tstr);

        this.setReady(false);

        if (req.getMaxIdleTime() == null) {
            this.setMaxIdleTime(0);
        } else {
            this.setMaxIdleTime(req.getMaxIdleTime());
        }

        this.setDescription(req.getDescription());

        this.setWebUserName(req.getWebUserName());

        this.setWebUserIp(req.getWebUserIp());

        this.setWebUserRole(req.getWebUserRole());

        if (req.getSessionType() == null || req.getSessionType().equalsIgnoreCase(NEW)) {
            this.setSessionType(NEW);
        } else if (req.getSessionType().equalsIgnoreCase(JOIN)) {
            this.setSessionType(JOIN);
        } else if (req.getSessionType().equalsIgnoreCase(WATCH)) {
            this.setSessionType(WATCH);
        } else if (req.getSessionType().equalsIgnoreCase(TAKE)) {
            this.setSessionType(TAKE);
        } else {
            this.setSessionType(NEW);
        }
        if (!this.getSessionType().equals(NEW)) {
            this.setParentToken(req.getParentToken());
            // don't care about other fields
            return;
        }

        this.setHost(req.getHost());
        if (req.getPort()
                == null || req.getPort() <= 0) {
            if (req.getConnectionType().equalsIgnoreCase(SSH_LC)) {
                this.setPort(22);
            } else {
                this.setPort(23);
            }
        } else {
            this.setPort(req.getPort());
        }

        this.setUsername(req.getUsername());

        this.setPassword(req.getPassword());

        this.setConnectionType(req.getConnectionType());

        if (req.getConnectionType().equalsIgnoreCase(SSH_LC)
                && StringUtils.isEmpty(req.getPassword())
                || req.getConnectionType().equalsIgnoreCase(TN3270_LC)) {
            logger.debug("forcing pty mode for: {}", req);

            this.setUsePty(true);
        } else {
            this.setUsePty(req.isUsePty());
        }

        //this.setAuditLogging(req.getAuditLogging());
        if (req.getAuditLogging()
                == null || req.getAuditLogging().equalsIgnoreCase("off")) {
            this.setAuditLogging("OFF");
        } else {
            this.setAuditLogging("ON");
        }

        this.setVisibleToAll(req.isVisibleToAll());

        logger.trace("after transfer: {}", this);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toStringExclude(this, "password", "logWriter", "lock");
    }
}
