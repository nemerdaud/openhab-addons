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

import static org.openhab.binding.lgthinq.lgservices.model.DeviceTypes.AIR_CONDITIONER;
import static org.openhab.binding.lgthinq.lgservices.model.DeviceTypes.fromDeviceTypeAcron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.LGThinqApiV1ClientServiceImpl;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACCapability;
import org.openhab.binding.lgthinq.lgservices.model.washer.WMCapability;

/**
 * The {@link LGThinqApiV1ClientServiceImpl}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class CapabilityFactory {
    private static final CapabilityFactory instance;
    static {
        instance = new CapabilityFactory();
    }

    public static final CapabilityFactory getInstance() {
        return instance;
    }

    public <T extends Capability> T create(Map<String, Object> rootMap, Class<T> capType) throws LGThinqApiException {
        DeviceTypes type = getDeviceType(rootMap);

        switch (type) {
            case AIR_CONDITIONER:
                return capType.cast(getAcCapabilities(rootMap));
            case WASHING_MACHINE:
                return capType.cast(getWmCapabilities(rootMap));
            default:
                throw new IllegalStateException("Unexpected capability. The type " + type + " was not implemented yet");
        }
    }

    private DeviceTypes getDeviceType(Map<String, Object> rootMap) {
        Map<String, String> infoMap = (Map<String, String>) rootMap.get("Info");
        Objects.requireNonNull(infoMap, "Unexpected error. Info node not present in capability schema");
        String productType = infoMap.get("productType");
        Objects.requireNonNull(infoMap, "Unexpected error. ProductType attribute not present in capability schema");
        DeviceTypes type = fromDeviceTypeAcron(productType);
        return type;
    }

    private WMCapability getWmCapabilities(Map<String, Object> rootMap) throws LGThinqApiException {
        LGAPIVerion version = discoveryAPIVersion(rootMap);
        if (version == LGAPIVerion.V2_0) {
            Map<String, Object> monValue = (Map<String, Object>) rootMap.get("MonitoringValue");
            Objects.requireNonNull(monValue, "Unexpected error. Info Config not present in capability schema");
            WMCapability wmCap = new WMCapability();

            Map<String, Object> courseMap = (Map<String, Object>) rootMap.get("Course");
            Objects.requireNonNull(courseMap, "Unexpected error. Info Config not present in capability schema");
            courseMap.forEach((k, v) -> {
                wmCap.addCourse(Objects.requireNonNull(((Map<String, String>) v).get("name"),
                        "Name property for course node must be present"), k);
            });

            loadMonValueCap(WMCapability.MonitoringCap.STATE, monValue, wmCap);
            loadMonValueCap(WMCapability.MonitoringCap.SOIL_WASH, monValue, wmCap);
            loadMonValueCap(WMCapability.MonitoringCap.SPIN, monValue, wmCap);
            loadMonValueCap(WMCapability.MonitoringCap.TEMPERATURE, monValue, wmCap);
            loadMonValueCap(WMCapability.MonitoringCap.RINSE, monValue, wmCap);
            if (monValue.get("doorLock") != null) {
                wmCap.setHasDoorLook(true);
            }
            if (monValue.get("turboWash") != null) {
                wmCap.setHasTurboWash(true);
            }
            return wmCap;
        } else {
            throw new LGThinqApiException(
                    "Version " + version.getValue() + " for Washers not supported for this binding.");
        }
    }

    private void loadMonValueCap(WMCapability.MonitoringCap monCap, Map<String, Object> monMap, WMCapability wmCap) {
        Map<String, Object> nodeMap = (Map<String, Object>) monMap.get(monCap.getValue());
        if (nodeMap == null) {
            // ignore feature, since it doe
            return;
        }
        Map<String, Object> map = (Map<String, Object>) nodeMap.get("valueMapping");
        Objects.requireNonNull(map, "Unexpected error. valueMapping attribute is mandatory");
        map.forEach((k, v) -> {
            wmCap.addMonitoringValue(monCap, Objects.requireNonNull(((Map<String, String>) v).get("label"),
                    "label property for course node must be present"), k);
        });
    }

    private ACCapability getAcCapabilities(Map<String, Object> rootMap) throws LGThinqApiException {
        LGAPIVerion version = discoveryAPIVersion(rootMap);
        if (version == LGAPIVerion.V1_0) {
            ACCapability acCap = new ACCapability();
            Map<String, Object> cap = (Map<String, Object>) rootMap.get("Value");
            if (cap == null) {
                throw new LGThinqApiException("Error extracting capabilities supported by the device");
            }

            Map<String, Object> opModes = (Map<String, Object>) cap.get("OpMode");
            if (opModes == null) {
                throw new LGThinqApiException("Error extracting opModes supported by the device");
            } else {
                Map<String, String> modes = new HashMap<String, String>();
                ((Map<String, String>) opModes.get("option")).forEach((k, v) -> {
                    modes.put(v, k);
                });
                acCap.setOpMod(modes);
            }
            Map<String, Object> fanSpeed = (Map<String, Object>) cap.get("WindStrength");
            if (fanSpeed == null) {
                throw new LGThinqApiException("Error extracting fanSpeed supported by the device");
            } else {
                Map<String, String> fanModes = new HashMap<String, String>();
                ((Map<String, String>) fanSpeed.get("option")).forEach((k, v) -> {
                    fanModes.put(v, k);
                });
                acCap.setFanSpeed(fanModes);

            }
            // Set supported modes for the device

            Map<String, Map<String, String>> supOpModes = (Map<String, Map<String, String>>) cap.get("SupportOpMode");
            acCap.setSupportedOpMode(new ArrayList<>(supOpModes.get("option").values()));
            acCap.getSupportedOpMode().remove("@NON");
            Map<String, Map<String, String>> supFanSpeeds = (Map<String, Map<String, String>>) cap
                    .get("SupportWindStrength");
            acCap.setSupportedFanSpeed(new ArrayList<>(supFanSpeeds.get("option").values()));
            acCap.getSupportedFanSpeed().remove("@NON");

            return acCap;
        } else {
            Map<String, Object> cap = (Map<String, Object>) rootMap.get("Value");
            if (cap == null) {
                throw new LGThinqApiException("Error extracting capabilities supported by the device");
            }
            ACCapability acCap = new ACCapability();
            Map<String, Object> opModes = (Map<String, Object>) cap.get("airState.opMode");
            if (opModes == null) {
                throw new LGThinqApiException("Error extracting opModes supported by the device");
            } else {
                Map<String, String> modes = new HashMap<String, String>();
                ((Map<String, String>) opModes.get("value_mapping")).forEach((k, v) -> {
                    modes.put(v, k);
                });
                acCap.setOpMod(modes);
            }
            Map<String, Object> fanSpeed = (Map<String, Object>) cap.get("airState.windStrength");
            if (fanSpeed == null) {
                throw new LGThinqApiException("Error extracting fanSpeed supported by the device");
            } else {
                Map<String, String> fanModes = new HashMap<String, String>();
                ((Map<String, String>) fanSpeed.get("value_mapping")).forEach((k, v) -> {
                    fanModes.put(v, k);
                });
                acCap.setFanSpeed(fanModes);

            }
            // Set supported modes for the device
            Map<String, Map<String, String>> supOpModes = (Map<String, Map<String, String>>) cap
                    .get("support.airState.opMode");
            acCap.setSupportedOpMode(new ArrayList<>(supOpModes.get("value_mapping").values()));
            acCap.getSupportedOpMode().remove("@NON");
            Map<String, Map<String, String>> supFanSpeeds = (Map<String, Map<String, String>>) cap
                    .get("support.airState.windStrength");
            acCap.setSupportedFanSpeed(new ArrayList<>(supFanSpeeds.get("value_mapping").values()));
            acCap.getSupportedFanSpeed().remove("@NON");
            return acCap;
        }
    }

    private LGAPIVerion discoveryAPIVersion(Map<String, Object> rootMap) {
        DeviceTypes type = getDeviceType(rootMap);
        switch (type) {
            case AIR_CONDITIONER:
                Map<String, Object> valueNode = getCapabilitySession(rootMap, AIR_CONDITIONER);
                if (valueNode.containsKey("support.airState.opMode")) {
                    return LGAPIVerion.V2_0;
                } else if (valueNode.containsKey("SupportOpMode")) {
                    return LGAPIVerion.V1_0;
                } else {
                    throw new IllegalStateException(
                            "Unexpected error. Can't find key node attributes to determine AC API version.");
                }

            case WASHING_MACHINE:
                return LGAPIVerion.V2_0;
            default:
                throw new IllegalStateException("Unexpected capability. The type " + type + " was not implemented yet");
        }
    }

    private Map<String, Object> getCapabilitySession(Map<String, Object> rootMap, DeviceTypes type) {
        switch (type) {
            case AIR_CONDITIONER:
                Map<String, Object> values = (Map<String, Object>) rootMap.get("Value");
                Objects.requireNonNull(values, "Unexpected error. Value node is expected for AC Capabilities");
                return values;
            case WASHING_MACHINE:
                Map<String, Object> config = (Map<String, Object>) rootMap.get("Config");
                Objects.requireNonNull(config, "Unexpected error. Config node is expected for Washing Machines");
                return config;
            default:
                throw new IllegalStateException("Unexpected capability. The type " + type + " was not implemented yet");
        }
    }
}
