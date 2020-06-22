/*
 * Copyright 2017 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.centraldogma.server.internal.api.converter;

import static com.google.common.base.Ascii.toLowerCase;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.lang.reflect.ParameterizedType;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.RequestConverterFunction;
import com.linecorp.centraldogma.common.Revision;

/**
 * A request converter that converts to {@link WatchRequest} when the request contains
 * {@link HttpHeaderNames#IF_NONE_MATCH}.
 */
public final class WatchRequestConverter implements RequestConverterFunction {

    private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(120);

    /**
     * Converts the specified {@code request} to a {@link WatchRequest} when the request has
     * {@link HttpHeaderNames#IF_NONE_MATCH}. {@code null} otherwise.
     */
    @Override
    @Nullable
    public WatchRequest convertRequest(
            ServiceRequestContext ctx, AggregatedHttpRequest request, Class<?> expectedResultType,
            @Nullable ParameterizedType expectedParameterizedResultType) throws Exception {

        final String ifNoneMatch = request.headers().get(HttpHeaderNames.IF_NONE_MATCH);
        if (isNullOrEmpty(ifNoneMatch)) {
            return null;
        }

        final Revision lastKnownRevision = new Revision(ifNoneMatch);
        final String prefer = request.headers().get(HttpHeaderNames.PREFER);
        final long timeoutMillis;
        if (!isNullOrEmpty(prefer)) {
            timeoutMillis = getTimeoutMillis(prefer);
        } else {
            timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        }

        return new WatchRequest(lastKnownRevision, timeoutMillis);
    }

    private static long getTimeoutMillis(String preferHeader) {
        final String prefer = toLowerCase(preferHeader.replaceAll("\\s+", ""));
        if (!prefer.startsWith("wait=")) {
            return rejectPreferHeader(preferHeader);
        }

        final long timeoutSeconds;
        try {
            timeoutSeconds = Long.parseLong(prefer.substring("wait=".length()));
        } catch (NumberFormatException e) {
            return rejectPreferHeader(preferHeader);
        }

        if (timeoutSeconds <= 0) {
            return rejectPreferHeader(preferHeader);
        }
        return TimeUnit.SECONDS.toMillis(timeoutSeconds);
    }

    private static long rejectPreferHeader(String preferHeader) {
        throw new IllegalArgumentException("invalid prefer header: " + preferHeader +
                                           " (expected: wait=seconds)");
    }

    public static class WatchRequest {
        private final Revision lastKnownRevision;
        private final long timeoutMillis;

        WatchRequest(Revision lastKnownRevision, long timeoutMillis) {
            this.lastKnownRevision = lastKnownRevision;
            this.timeoutMillis = timeoutMillis;
        }

        public Revision lastKnownRevision() {
            return lastKnownRevision;
        }

        public long timeoutMillis() {
            return timeoutMillis;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                              .add("lastKnownRevision", lastKnownRevision)
                              .add("timeoutMillis", timeoutMillis)
                              .toString();
        }
    }
}
