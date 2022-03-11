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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webterminal.connection.Connection;
import static org.webterminal.constant.Constants.LF;

public class TimedMatchAndSubmit {

    private static final Logger logger = LoggerFactory.getLogger(TimedMatchAndSubmit.class);
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * don't want to be stuck while reading inputStream,
     * not checking anything after data submit either
     *
     * @param seconds How many seconds to wait before timeout
     * @param in inputStream to read from
     * @param prompts prompts to check against
     * @param data what to send in if matched
     * @param connection used to send data
     * @throws IOException
     * @throws InterruptedException
     */
    public static void matchPromptToSubmit(int seconds, InputStream in, String[] prompts, String data, Connection connection) throws IOException, InterruptedException {
        logger.debug("start matchPromptToSubmit");

        Future<String> future = executorService.submit(new Callable<String>() {
            @Override
            public String call() {
                logger.debug("thread for matchPromptToSubmit start");
                // all patterns happen to end with a space, use some optimization
                char lastChar = ' ';
                StringBuilder sb = new StringBuilder();
                boolean done = false;
                while (!done) {
                    int ch;
                    try {
                        ch = in.read();
                    } catch (IOException ex) {
                        logger.debug("thread for matchPromptToSubmit end: {}", ex.getMessage());
                        return null;
                    }
                    if (ch == -1) {
                        logger.debug("thread for matchPromptToSubmit EOF");
                        return null;
                    }
                    logger.trace("read [{}] {}", String.valueOf((char) ch), ch);
                    sb.append((char) ch);
                    if (ch == lastChar) {
                        for (String pattern : prompts) {
                            logger.trace("check [{}] against prompt [{}]", sb, pattern);
                            if (sb.toString().toLowerCase().endsWith(pattern)) {
                                logger.debug("prompt matched [{}]", sb);
                                done = true;
                                break;
                            }
                        }
                    }

                }
                logger.debug("thread for matchPromptToSubmit end");

                return sb.toString();
            }
        });

        /*
        ExecutionException    - if the computation threw an exception 
        CancellationException - if the computation was cancelled
        InterruptedException  - if the current thread was interrupted while waiting 
        TimeoutException      - if the wait timed out
         */
        try {
            String result = future.get(seconds, TimeUnit.SECONDS);
            if (result == null) {
                throw new IOException("EOF or IOException Encountered");
            } else {
                logger.debug("thread matchPromptToSubmit ok [{}], sending data", result);
                connection.send(data + LF);
            }
        } catch (TimeoutException ex) {
            logger.debug("TimeoutException");
            // not all tasks actually can be canceled/interrupted, but we're ok
            // as the task will exit when reading on filehandle that's going to be closed
            boolean cancelled = future.cancel(true);
            logger.debug("task cancelled? {}", cancelled);

            throw new IOException("Timeout");
        } catch (InterruptedException ex) {
            logger.debug("InterruptedException: {}", ex.getMessage());
            throw new IOException(ex);
        } catch (ExecutionException ex) {
            logger.debug("ExecutionException: {}", ex.getMessage());
            throw new IOException(ex);
        }
    }

}
