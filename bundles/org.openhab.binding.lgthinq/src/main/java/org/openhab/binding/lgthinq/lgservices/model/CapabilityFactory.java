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

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CAP_AC_COOL_JET;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.CAP_AC_COOL_JET_COMMAND_OFF;
import static org.openhab.binding.lgthinq.lgservices.model.DeviceTypes.*;

import java.util.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.LGThinQACApiV1ClientServiceImpl;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACCapability;
import org.openhab.binding.lgthinq.lgservices.model.dryer.DryerCapability;
import org.openhab.binding.lgthinq.lgservices.model.washer.WasherCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link LGThinQACApiV1ClientServiceImpl}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class CapabilityFactory {
    private static final CapabilityFactory instance;
    static {
        instance = new CapabilityFactory();
    }
    private static final Logger logger = LoggerFactory.getLogger(CapabilityFactory.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static CapabilityFactory getInstance() {
        return instance;
    }

    public <C extends Capability> C create(Map<String, Object> rootMap, Class<C> clazz) throws LGThinqApiException {
        DeviceTypes type = getDeviceType(rootMap);

        switch (type) {
            case AIR_CONDITIONER:
                return clazz.cast(getAcCapabilities(rootMap));
            case WASHING_MACHINE:
                return clazz.cast(getWmCapabilities(rootMap));
            case DRYER:
                return clazz.cast(getDrCapabilities(rootMap));
            default:
                throw new IllegalStateException("Unexpected capability. The type " + type + " was not implemented yet");
        }
    }

    private Capability getDrCapabilities(Map<String, Object> rootMap) throws LGThinqApiException {
        LGAPIVerion version = discoveryAPIVersion(rootMap);
        if (version == LGAPIVerion.V2_0) {
            Map<String, Object> monValue = (Map<String, Object>) rootMap.get("MonitoringValue");
            Objects.requireNonNull(monValue, "Unexpected error. MonitoringValue not present in capability schema");
            DryerCapability drCap = new DryerCapability();

            Map<String, Object> courseMap = (Map<String, Object>) rootMap.get("Course");
            Objects.requireNonNull(courseMap, "Unexpected error. Course not present in capability schema");
            courseMap.forEach((k, v) -> {
                drCap.addCourse(k, Objects.requireNonNull(((Map<String, String>) v).get("_comment"),
                        "_comment property for course node must be present"));
            });
            drCap.addCourse("NOT_SELECTED", "-");

            Map<String, Object> smartCourseMap = (Map<String, Object>) rootMap.get("SmartCourse");
            Objects.requireNonNull(smartCourseMap,
                    "Unexpected error. Info SmartCourse not present in capability schema");
            smartCourseMap.forEach((k, v) -> {
                drCap.addSmartCourse(k, Objects.requireNonNull(((Map<String, String>) v).get("_comment"),
                        "_comment property for smartCourse node must be present"));
            });
            drCap.addSmartCourse("NOT_SELECTED", "-");

            if (monValue.get("ChildLock") != null) {
                drCap.setChildLock(true);
            }
            if (monValue.get("RemoteStart") != null) {
                drCap.setRemoteStart(true);
            }
            loadDrMonValueCapV2(DryerCapability.MonitoringCap.STATE_V2, monValue, drCap, "label");
            loadDrMonValueCapV2(DryerCapability.MonitoringCap.DRY_LEVEL_V2, monValue, drCap, "label");
            loadDrMonValueCapV2(DryerCapability.MonitoringCap.ERROR_V2, monValue, drCap, "_comment");
            loadDrMonValueCapV2(DryerCapability.MonitoringCap.PROCESS_STATE_V2, monValue, drCap, "label");

            return drCap;
            // } else if (version == LGAPIVerion.V1_0) {
            // Map<String, Object> monValue = (Map<String, Object>) rootMap.get("Value");
            // Objects.requireNonNull(monValue, "Unexpected error. MonitoringValue not present in capability schema");
            // DryerCapability drCap = new DryerCapability();
            //
            // Map<String, Object> courseMap = (Map<String, Object>) rootMap.get("Course");
            // Objects.requireNonNull(courseMap, "Unexpected error. Course not present in capability schema");
            // courseMap.forEach((k, v) -> {
            // drCap.addCourse(k, Objects.requireNonNull(((Map<String, String>) v).get("_comment"),
            // "_comment property for course node must be present"));
            // });
            // drCap.addCourse("NOT_SELECTED", "-");
            //
            // Map<String, Object> smartCourseMap = (Map<String, Object>) rootMap.get("SmartCourse");
            // Objects.requireNonNull(smartCourseMap,
            // "Unexpected error. Info SmartCourse not present in capability schema");
            // smartCourseMap.forEach((k, v) -> {
            // drCap.addSmartCourse(k, Objects.requireNonNull(((Map<String, String>) v).get("_comment"),
            // "_comment property for smartCourse node must be present"));
            // });
            // drCap.addSmartCourse("NOT_SELECTED", "-");
            //
            // if (monValue.get("childLock") != null) {
            // drCap.setChildLock(true);
            // }
            // if (monValue.get("remoteStart") != null) {
            // drCap.setRemoteStart(true);
            // }
            // // mapping states
            // Map<String, Object> stateMap = (Map<String, Object>) monValue.get("State");
            // Map<String, String> options = (Map<String, String>) stateMap.get("option");
            // options.forEach((k,v) -> {
            // drCap.addMonitoringValue(DryerCapability.MonitoringCap.STATE_V1, k, v);
            // });
            // Map<String, Object> errorMap = (Map<String, Object>) rootMap.get("Error");
            // Map<String, String> options = (Map<String, String>) stateMap.get("option");
            // options.forEach((k,v) -> {
            // drCap.addMonitoringValue(DryerCapability.MonitoringCap.STATE_V1, k, v);
            // });
            //
            // loadDrMonValueCapV1(DryerCapability.MonitoringCap.ERROR_V1, monValue, drCap, "_comment");
            // loadDrMonValueCapV1(DryerCapability.MonitoringCap.PROCESS_STATE_V1, monValue, drCap, "label");
            //
            // return drCap;
        } else {
            throw new LGThinqApiException(
                    "Version " + version.getValue() + " for Washers not supported for this binding.");
        }
    }

    private DeviceTypes getDeviceType(Map<String, Object> rootMap) {
        Map<String, String> infoMap = (Map<String, String>) rootMap.get("Info");
        Objects.requireNonNull(infoMap, "Unexpected error. Info node not present in capability schema");
        String productType = infoMap.get("productType");
        String modelType = infoMap.get("modelType");
        Objects.requireNonNull(infoMap, "Unexpected error. ProductType attribute not present in capability schema");
        DeviceTypes type = fromDeviceTypeAcron(productType, modelType);
        return type;
    }

    private WasherCapability getWmCapabilities(Map<String, Object> rootMap) throws LGThinqApiException {
        LGAPIVerion version = discoveryAPIVersion(rootMap);
        if (version == LGAPIVerion.V2_0) {
            Map<String, Object> monValue = (Map<String, Object>) rootMap.get("MonitoringValue");
            Objects.requireNonNull(monValue, "Unexpected error. MonitoringValue not present in capability schema");
            WasherCapability wmCap = new WasherCapability();

            Map<String, Object> courseMap = (Map<String, Object>) rootMap.get("Course");
            Objects.requireNonNull(courseMap, "Unexpected error. Course not present in capability schema");
            courseMap.forEach((k, v) -> {
                wmCap.addCourse(k, Objects.requireNonNull(((Map<String, String>) v).get("_comment"),
                        "_comment property for course node must be present"));
            });
            wmCap.addCourse("NOT_SELECTED", "Not Selected");

            Map<String, Object> smartCourseMap = (Map<String, Object>) rootMap.get("SmartCourse");
            Objects.requireNonNull(smartCourseMap,
                    "Unexpected error. Info SmartCourse not present in capability schema");
            smartCourseMap.forEach((k, v) -> {
                wmCap.addSmartCourse(k, Objects.requireNonNull(((Map<String, String>) v).get("_comment"),
                        "_comment property for smartCourse node must be present"));
            });
            wmCap.addSmartCourse("NOT_SELECTED", "Not Selected");

            loadWmMonValueCapV2(WasherCapability.MonitoringCap.STATE_V2, monValue, wmCap);
            loadWmMonValueCapV2(WasherCapability.MonitoringCap.SOIL_WASH_V2, monValue, wmCap);
            loadWmMonValueCapV2(WasherCapability.MonitoringCap.SPIN_V2, monValue, wmCap);
            loadWmMonValueCapV2(WasherCapability.MonitoringCap.TEMPERATURE_V2, monValue, wmCap);
            loadWmMonValueCapV2(WasherCapability.MonitoringCap.RINSE_V2, monValue, wmCap);
            if (monValue.get("doorLock") != null) {
                wmCap.setHasDoorLook(true);
            }
            if (monValue.get("turboWash") != null) {
                wmCap.setHasTurboWash(true);
            }
            return wmCap;
        } else if (version == LGAPIVerion.V1_0) {
            Map<String, Object> monValue = (Map<String, Object>) rootMap.get("Value");
            Objects.requireNonNull(monValue, "Unexpected error. Value not present in capability schema");
            WasherCapability wmCap = new WasherCapability();

            Map<String, Object> courseMap = (Map<String, Object>) rootMap.get("Course");
            Objects.requireNonNull(courseMap, "Unexpected error. Course not present in capability schema");
            courseMap.forEach((k, v) -> {
                wmCap.addCourse(k, Objects.requireNonNull(((Map<String, String>) v).get("_comment"),
                        "_comment property for course node must be present"));
            });
            wmCap.addCourse("0", "NOT SELECTED");

            Map<String, Object> smartCourseMap = (Map<String, Object>) rootMap.get("SmartCourse");
            Objects.requireNonNull(smartCourseMap,
                    "Unexpected error. Info SmartCourse not present in capability schema");
            smartCourseMap.forEach((k, v) -> {
                wmCap.addSmartCourse(k, Objects.requireNonNull(((Map<String, String>) v).get("_comment"),
                        "_comment property for smartCourse node must be present"));
            });
            wmCap.addSmartCourse("0", "NOT SELECTED");

            if (monValue.get("ChildLock") != null) {
                wmCap.setHasDoorLook(true);
            }

            Map<String, Object> errorMap = (Map<String, Object>) rootMap.get("Error");

            errorMap.forEach((k, v) -> {
                Map<String, String> entry = (Map<String, String>) v;
                wmCap.addMonitoringValue(WasherCapability.MonitoringCap.ERROR_V1,
                        Objects.requireNonNull(entry.get("label")), k);
            });
            // mapping states
            loadWmMonValueCapV1(WasherCapability.MonitoringCap.STATE_V1, monValue, wmCap);
            loadWmMonValueCapV1(WasherCapability.MonitoringCap.SOIL_WASH_V1, monValue, wmCap);
            loadWmMonValueCapV1(WasherCapability.MonitoringCap.SPIN_V1, monValue, wmCap);
            loadWmMonValueCapV1(WasherCapability.MonitoringCap.TEMPERATURE_V1, monValue, wmCap);
            loadWmMonValueCapV1(WasherCapability.MonitoringCap.RINSE_V1, monValue, wmCap);

            loadMonitoringSessionV1(rootMap, wmCap);

            return wmCap;
        } else {
            throw new LGThinqApiException(
                    "Version " + version.getValue() + " for Washers not supported for this binding.");
        }
    }

    private void loadMonitoringSessionV1(Map<String, Object> rootMap, WasherCapability wmCap) {
        Map<String, Object> mon = (Map<String, Object>) rootMap.get("Monitoring");
        if (mon == null) {
            logger.warn("No monitoring session defined in the cap data.");
        } else {
            MonitoringResultFormat format = MonitoringResultFormat.getFormatOf((String) mon.get("type"));
            if (!MonitoringResultFormat.UNKNOWN_FORMAT.equals(format)) {
                wmCap.setMonitoringDataFormat(format);
            }
            if (MonitoringResultFormat.BINARY_FORMAT.equals(format)) {
                // load binary protocol
                List<MonitoringBinaryProtocol> pojos = mapper.convertValue(mon.get("protocol"),
                        new TypeReference<List<MonitoringBinaryProtocol>>() {
                        });
                wmCap.setMonitoringBinaryProtocol(pojos);
            }
        }
    }

    private void loadWmMonValueCapV2(WasherCapability.MonitoringCap monCap, Map<String, Object> monMap,
            WasherCapability wmCap) {
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

    private void loadWmMonValueCapV1(WasherCapability.MonitoringCap monCap, Map<String, Object> monMap,
            WasherCapability wmCap) {
        Map<String, Object> nodeMap = (Map<String, Object>) monMap.get(monCap.getValue());
        if (nodeMap == null) {
            // ignore feature, since it doe
            return;
        }
        Map<String, String> map = (Map<String, String>) nodeMap.get("option");
        Objects.requireNonNull(map, "Unexpected error. option attribute is mandatory");
        map.forEach((k, v) -> {
            wmCap.addMonitoringValue(monCap, v, k);
        });
    }

    private void loadDrMonValueCapV2(DryerCapability.MonitoringCap monCap, Map<String, Object> monMap,
            DryerCapability dryerCapability, String valueAttribute) {
        Map<String, Object> nodeMap = (Map<String, Object>) monMap.get(monCap.getValue());
        if (nodeMap == null) {
            // ignore feature, since it doe
            return;
        }
        Map<String, Object> map = (Map<String, Object>) nodeMap.get("valueMapping");
        Objects.requireNonNull(map, "Unexpected error. valueMapping attribute is mandatory");
        map.forEach((k, v) -> {
            dryerCapability.addMonitoringValue(monCap, k, Objects.requireNonNull(
                    ((Map<String, String>) v).get(valueAttribute), "label property for course node must be present"));
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

            // set Cool jetMode supportability
            Map<String, Map<String, String>> supJetModes = (Map<String, Map<String, String>>) cap.get("Jet");
            if (supJetModes != null) {
                (supJetModes.get("option")).forEach((k, v) -> {
                    if (CAP_AC_COOL_JET.containsKey(v)) {
                        acCap.setJetModeAvailable(true);
                        acCap.setCoolJetModeCommandOn(k);
                    } else if (CAP_AC_COOL_JET_COMMAND_OFF.equals(v)) {
                        acCap.setCoolJetModeCommandOff(k);
                    }
                });
            }

            Map<String, Object> mon = (Map<String, Object>) rootMap.get("Monitoring");
            if (mon == null) {
                logger.warn("No monitoring session defined in the cap data.");
            } else {
                MonitoringResultFormat format = MonitoringResultFormat.getFormatOf((String) mon.get("type"));
                if (!MonitoringResultFormat.UNKNOWN_FORMAT.equals(format)) {
                    acCap.setMonitoringDataFormat(format);
                }
            }
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

            // set Cool jetMode supportability
            Map<String, Map<String, String>> supJetModes = (Map<String, Map<String, String>>) cap
                    .get("airState.wMode.jet");
            if (supJetModes != null) {
                (supJetModes.get("value_mapping")).forEach((k, v) -> {
                    Map<String, String> jetModes = new HashMap<String, String>();
                    if (CAP_AC_COOL_JET.containsKey(v)) {
                        acCap.setJetModeAvailable(true);
                        acCap.setCoolJetModeCommandOn(k);
                    } else if (CAP_AC_COOL_JET_COMMAND_OFF.equals(v)) {
                        acCap.setCoolJetModeCommandOff(k);
                    }
                });
            }
            Map<String, Object> info = (Map<String, Object>) rootMap.get("Info");
            if (info == null) {
                logger.warn("No info session defined in the cap data.");
            } else {
                MonitoringResultFormat format = MonitoringResultFormat.getFormatOf((String) info.get("model"));
                if (!MonitoringResultFormat.UNKNOWN_FORMAT.equals(format)) {
                    acCap.setMonitoringDataFormat(format);
                }
            }
            return acCap;
        }
    }

    private LGAPIVerion discoveryAPIVersion(Map<String, Object> rootMap) {
        DeviceTypes type = getDeviceType(rootMap);
        switch (type) {
            case AIR_CONDITIONER:
                Map<String, Object> valueNode = (Map<String, Object>) rootMap.get("Value");
                if (valueNode.containsKey("support.airState.opMode")) {
                    return LGAPIVerion.V2_0;
                } else if (valueNode.containsKey("SupportOpMode")) {
                    return LGAPIVerion.V1_0;
                } else {
                    throw new IllegalStateException(
                            "Unexpected error. Can't find key node attributes to determine AC API version.");
                }

            case WASHING_MACHINE:
            case DRYER:
                if (rootMap.containsKey("Value")) {
                    return LGAPIVerion.V1_0;
                } else if (rootMap.containsKey("MonitoringValue")) {
                    return LGAPIVerion.V2_0;
                } else {
                    throw new IllegalStateException(
                            "Unexpected error. Can't find key node attributes to determine AC API version.");
                }
            default:
                throw new IllegalStateException("Unexpected capability. The type " + type + " was not implemented yet");
        }
    }
}
