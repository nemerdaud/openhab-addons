/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.lgthinq.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RestResult} result from rest calls
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class RestResult {
    private final String jsonResponse;
    private final int resultCode;

    private final Logger logger = LoggerFactory.getLogger(RestResult.class);

    public RestResult(String jsonResponse, int resultCode) {
        this.jsonResponse = jsonResponse;
        this.resultCode = resultCode;
        logger.debug("API result {}", this.jsonResponse);
    }

    public String getJsonResponse() {
        return jsonResponse;
    }

    public int getStatusCode() {
        return resultCode;
    }
}
