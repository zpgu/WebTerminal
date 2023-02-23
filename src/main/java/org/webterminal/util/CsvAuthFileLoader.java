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

import org.webterminal.pojo.CsvUserInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

public class CsvAuthFileLoader extends FileWatchdog {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CsvAuthFileLoader.class);
    private final InMemoryUserDetailsManager mgr;
    private static HashMap<String, String> loaded = new HashMap<>();
    private final PasswordEncoder passwordEncoder;

    /**
     *
     * @param filename
     * @param mgr
     * @param passwordEncoder
     */
    public CsvAuthFileLoader(String filename, InMemoryUserDetailsManager mgr, PasswordEncoder passwordEncoder) {
        super(filename);
        this.mgr = mgr;
        this.passwordEncoder = passwordEncoder;
        
        logger.debug("cstor CsvUserFileLoader filename [{}]", filename);

        // put some default starter accounts
        mgr.createUser(User.withUsername("admin")
                .password(passwordEncoder.encode("adminpass"))
                .roles("ADMIN")
                .build());
        mgr.createUser(User.withUsername("user")
                .password(passwordEncoder.encode("userpass"))
                .roles("USER")
                .build());

        loaded.put("admin", "ADMIN");
        loaded.put("user", "USER");

        logger.info("AUTH default users loaded");
    }

    /**
     *
     */
    public void reload() {
        if (StringUtils.isNotBlank(filename) && mgr != null) {
            try {
                logger.debug("read auth csv filename {}", filename);
                File cfile = new File(filename);
                List<CsvUserInfo> userList;
                try (InputStream in = new FileInputStream(cfile)) {
                    userList = CsvFileReader.read(CsvUserInfo.class, in);
                }
                logger.trace("{} CONTENT {}", filename, userList);
                HashMap<String, String> newload = new HashMap<>();
                for (CsvUserInfo user : userList) {
                    if (StringUtils.isNoneBlank(user.getLogin())) {
                        if (loaded.containsKey(user.getLogin())) {
                            logger.debug("AUTH upd {}", user);
                            mgr.updateUser(User.withUsername(user.getLogin())
                                    .password(passwordEncoder.encode(user.getPassword()))
                                    .roles(user.getRole().toUpperCase())
                                    .build());
                        } else {
                            logger.debug("AUTH add {}", user);
                            mgr.createUser(User.withUsername(user.getLogin())
                                    .password(passwordEncoder.encode(user.getPassword()))
                                    .roles(user.getRole().toUpperCase())
                                    .build());
                            loaded.put(user.getLogin(), user.getRole());
                        }
                        newload.put(user.getLogin(), user.getRole());
                    }
                }

                if (!newload.isEmpty()) {
                    // remove users in loaded, but not in newload
                    ArrayList<String> sorted = new ArrayList<>(loaded.keySet());
                    Collections.sort(sorted);
                    for (String name : sorted) {
                        if (!newload.containsKey(name)) {
                            logger.info("AUTH del [login={}]", name);

                            // TODO: knock out all sessions owned by name???
                            mgr.deleteUser(name);
                        }
                    }
                    loaded = newload;

                    logger.info("AUTH PROVISIONED USER COUNT={}:", loaded.size());
                    // dump out newly installed list
                    sorted = new ArrayList<>(loaded.keySet());
                    Collections.sort(sorted);
                    for (String name : sorted) {
                        logger.info("  AUTH {} -> {}", loaded.get(name), name);
                    }

                } else {
                    logger.warn("no valid csv entry found in {}, nothing changed", filename);
                }

            } catch (IOException ex) {
                logger.warn("csv file {} access error, nothing loaded: {}", filename, ex);
            }
        } else {
            if (mgr == null) {
                logger.info("AUTH csv file not loaded yet: filename=[{}]", filename);
            } else {
                logger.info("AUTH csv file not configured");
            }
        }
    }

    /**
     *
     */
    @Override
    protected void doOnChange() {
        reload();
    }
}
