/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy;

import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Factory for route/affinity iterator parser. This implementation lazily parses routes while supporting ranked routing. The
 * iterator never creates new String instances but returns a CharSequence wrapper from the existing session ID.
 *
 * @author Radoslav Husar
 */
public class RouteIteratorFactory {

    public enum ParsingCompatibility {
        MOD_JK,
        MOD_CLUSTER,
    }

    private final ParsingCompatibility parsing;
    private final String delimiter;

    /**
     * @param parsingCompatibility parsing compatibility behavior
     * @param rankedRouteDelimiter String sequence to split routes at to support ranked affinity parsing; {@code null} disables the support
     */
    public RouteIteratorFactory(ParsingCompatibility parsingCompatibility, String rankedRouteDelimiter) {
        this.parsing = parsingCompatibility;
        this.delimiter = rankedRouteDelimiter;
    }

    /**
     * Returns an {@link Iterator<CharSequence>} of routes.
     *
     * @param sessionId String of sessionID from the cookie/parameter possibly including encoded/appended affinity/route information
     * @return routes iterator; never returns {@code null}
     */
    public Iterator<CharSequence> iterator(String sessionId) {
        return new RouteIterator(sessionId);
    }

    private class RouteIterator implements Iterator<CharSequence> {

        private final String sessionId;

        private boolean nextResolved;
        private int nextPos;
        private CharSequence next;

        RouteIterator(String sessionId) {
            this.sessionId = sessionId;

            int index = (sessionId == null) ? -1 : sessionId.indexOf('.');
            if (index == -1) {
                // Case where there is no routing information at all.
                this.nextResolved = true;
                this.next = null;
            } else {
                // Case where ranked route support is not enabled
                nextPos = index + 1;
            }
        }

        @Override
        public boolean hasNext() {
            resolveNext();

            return next != null;
        }

        @Override
        public CharSequence next() {
            resolveNext();

            if (next != null) {
                CharSequence result = next;
                nextResolved = (delimiter == null);
                next = null;

                return result;
            }
            throw new NoSuchElementException();
        }

        private void resolveNext() {
            if (!nextResolved) {
                if (delimiter == null) {
                    if (parsing == ParsingCompatibility.MOD_JK) {
                        // mod_jk aka LoadBalancingProxyClient uses mod_jk parsing mechanism though never supports domain
                        // it treats route only as the sequence after the first "." but before the second ".";
                        // i.e. does not allow "." in route
                        int last = sessionId.indexOf('.', nextPos);
                        if (last == -1) {
                            last = sessionId.length();
                        }
                        next = CharBuffer.wrap(sessionId, nextPos, last);
                    } else {
                        // mod_cluster treats everything after first "." as the route; i.e. allows "." in route
                        next = CharBuffer.wrap(sessionId, nextPos, sessionId.length());
                    }
                } else if (nextPos >= sessionId.length()) {
                    next = null;
                } else {
                    int currentPos = sessionId.indexOf(delimiter, nextPos);
                    next = CharBuffer.wrap(sessionId, nextPos, (currentPos != -1) ? currentPos : sessionId.length());
                    nextPos += next.length() + delimiter.length();
                }
                nextResolved = true;
            }
        }

    }
}
