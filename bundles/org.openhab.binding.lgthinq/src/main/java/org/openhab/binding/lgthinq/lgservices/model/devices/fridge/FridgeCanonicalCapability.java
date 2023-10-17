/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.lgthinq.lgservices.model.devices.fridge;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.lgservices.model.AbstractCapability;
import org.openhab.binding.lgthinq.lgservices.model.CommandDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link FridgeCanonicalCapability}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class FridgeCanonicalCapability extends AbstractCapability<FridgeCanonicalCapability>
        implements FridgeCapability {

    private static final Logger logger = LoggerFactory.getLogger(FridgeCanonicalCapability.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, String> fridgeTempCMap = new LinkedHashMap<String, String>();
    private final Map<String, String> fridgeTempFMap = new LinkedHashMap<String, String>();
    private final Map<String, String> freezerTempCMap = new LinkedHashMap<String, String>();
    private final Map<String, String> freezerTempFMap = new LinkedHashMap<String, String>();
    private final Map<String, String> tempUnitMap = new LinkedHashMap<String, String>();
    private final Map<String, String> icePlusMap = new LinkedHashMap<String, String>();;
    private final Map<String, String> freshAirFilterMap = new LinkedHashMap<String, String>();;
    private final Map<String, String> waterFilterMap = new LinkedHashMap<String, String>();;
    private final Map<String, String> expressModeMap = new LinkedHashMap<String, String>();;
    private final Map<String, String> smartSavingMap = new LinkedHashMap<String, String>();;
    private final Map<String, String> activeSavingMap = new LinkedHashMap<String, String>();;
    private final Map<String, String> atLeastOneDoorOpenMap = new LinkedHashMap<>();
    private final Map<String, CommandDefinition> commandsDefinition = new LinkedHashMap<>();

    public Map<String, String> getFridgeTempCMap() {
        return fridgeTempCMap;
    }

    public Map<String, String> getFridgeTempFMap() {
        return fridgeTempFMap;
    }

    public Map<String, String> getFreezerTempCMap() {
        return freezerTempCMap;
    }

    public Map<String, String> getFreezerTempFMap() {
        return freezerTempFMap;
    }

    @Override
    public Map<String, String> getTempUnitMap() {
        return tempUnitMap;
    }

    @Override
    public Map<String, String> getIcePlusMap() {
        return icePlusMap;
    }

    @Override
    public Map<String, String> getFreshAirFilterMap() {
        return freshAirFilterMap;
    }

    @Override
    public Map<String, String> getWaterFilterMap() {
        return waterFilterMap;
    }

    @Override
    public Map<String, String> getExpressModeMap() {
        return expressModeMap;
    }

    @Override
    public Map<String, String> getSmartSavingMap() {
        return smartSavingMap;
    }

    @Override
    public Map<String, String> getActiveSavingMap() {
        return activeSavingMap;
    }

    @Override
    public Map<String, String> getAtLeastOneDoorOpenMap() {
        return atLeastOneDoorOpenMap;
    }

    public Map<String, CommandDefinition> getCommandsDefinition() {
        return commandsDefinition;
    }
}
