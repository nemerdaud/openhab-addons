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
package org.openhab.binding.lgthinq.lgservices.model;

/**
 * The {@link ResultCodes}
 *
 * @author Nemer Daud - Initial contribution
 */
public enum ResultCodes {
    OK("Success", "0000"),
    DEVICE_NOT_RESPONSE("Device Not Response", "0111", "0103", "0104", "0106"),
    LOGIN_FAILED("Login Failed", "0004", "0102", "0110", "0114"),
    NOT_SUPPORTED_CONTROL("Control is not supported", "0005", "0012", "8001"),
    LG_SERVER_ERROR("Control is not supported", "8101", "8102", "8203", "8204", "8205", "8206", "8207", "9003", "9004",
            "9005"),
    DUPLICATED_DATA("Duplicated Data", "0008", "0013"),
    UNKNOWN("UNKNOWN", "");

    private final String description;
    private final String[] code;

    ResultCodes(String description, String... code) {
        this.code = code;
        this.description = description;
    }

    ResultCodes fromCode(String code) {
        switch (code) {
            case "0000":
            case "0001":
                return OK;
            case "0004":
            case "0102":
            case "0110":
            case "0114":
                return LOGIN_FAILED;
            case "0008":
            case "0013":
                return DUPLICATED_DATA;
            case "0005":
            case "0012":
            case "8001":
                return NOT_SUPPORTED_CONTROL;
            case "0111":
            case "0103":
            case "0104":
            case "0106":
                return DEVICE_NOT_RESPONSE;
            case "8101":
            case "8102":
            case "8203":
            case "8204":
            case "8205":
            case "8206":
            case "8207":
            case "9003":
            case "9004":
            case "9005":
                return LG_SERVER_ERROR;
            default:
                return UNKNOWN;

        }
    }
}
