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
 * The {@link DeviceTypes}
 *
 * @author Nemer Daud - Initial contribution
 */
public enum DeviceTypes {
    AIR_CONDITIONER(401, "AC", ""),
    WASHING_MACHINE(201, "WM", ""),
    DRYER(202, "DR", "Dryer"),
    FRIDGE(101, "DR", "Fridge"),
    UNKNOWN(-1, "", "");

    private final int deviceTypeId;
    private final String deviceTypeAcron;
    private final String deviceSubModel;

    public String deviceTypeAcron() {
        return deviceTypeAcron;
    }

    public int deviceTypeId() {
        return deviceTypeId;
    }

    public String deviceSubModel() {
        return deviceSubModel;
    }

    public static DeviceTypes fromDeviceTypeId(int deviceTypeId) {
        switch (deviceTypeId) {
            case 401:
                return AIR_CONDITIONER;
            case 201:
                return WASHING_MACHINE;
            case 202:
                return DRYER;
            case 101:
                return FRIDGE;
            default:
                return UNKNOWN;
        }
    }

    public static DeviceTypes fromDeviceTypeAcron(String deviceTypeAcron, String modelType) {
        switch (deviceTypeAcron) {
            case "AC":
                return AIR_CONDITIONER;
            case "WM":
                if ("Dryer".equals(modelType)) {
                    return DRYER;
                } else {
                    return WASHING_MACHINE;
                }
            default:
                return UNKNOWN;
        }
    }

    DeviceTypes(int i, String n, String submodel) {
        this.deviceTypeId = i;
        this.deviceTypeAcron = n;
        this.deviceSubModel = submodel;
    }
}
