/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.lgthinq.internal.discovery;

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.*;
import static org.openhab.core.thing.Thing.PROPERTY_MODEL_ID;

import java.time.Instant;
import java.util.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.errors.LGThinqException;
import org.openhab.binding.lgthinq.internal.handler.LGThinQBridgeHandler;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientServiceFactory;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientServiceFactory.LGThinQGeneralApiClientService;
import org.openhab.binding.lgthinq.lgservices.model.LGDevice;
import org.openhab.core.config.discovery.*;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGThinqDiscoveryService}
 *
 * @author Nemer Daud - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = LGThinqDiscoveryService.class)
@NonNullByDefault
public class LGThinqDiscoveryService extends AbstractThingHandlerDiscoveryService<LGThinQBridgeHandler> {

    private final Logger logger = LoggerFactory.getLogger(LGThinqDiscoveryService.class);
    private @Nullable ThingUID bridgeHandlerUID;
    private @Nullable LGThinQGeneralApiClientService lgApiClientService;

    public LGThinqDiscoveryService() {
        super(LGThinQBridgeHandler.class, SUPPORTED_THING_TYPES, SEARCH_TIME);
    }

    @Override
    public void initialize() {
        bridgeHandlerUID = thingHandler.getThing().getUID();
        // thingHandler is the LGThinQBridgeHandler
        thingHandler.registerDiscoveryListener(this);
        lgApiClientService = LGThinQApiClientServiceFactory
                .newGeneralApiClientService(thingHandler.getHttpClientFactory());
        super.initialize();
    }

    @Override
    protected void startScan() {
        logger.debug("Scan started");
        // thingHandler is the LGThinQBridgeHandler
        thingHandler.runDiscovery();
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan(), thingHandler.getThing().getUID());
    }

    public void removeLgDeviceDiscovery(LGDevice device) {
        logger.debug("Thing removed from discovery: {}", device.getDeviceId());
        try {
            ThingUID thingUID = getThingUID(device);
            thingRemoved(thingUID);
        } catch (LGThinqException e) {
            logger.error("Error getting Thing UID");
        }
    }

    public void addLgDeviceDiscovery(LGDevice device) {
        logger.debug("Thing added to discovery: {}", device.getDeviceId());
        String modelId = device.getModelName();
        ThingUID thingUID;
        ThingTypeUID thingTypeUID;
        try {
            // load capability to cache and troubleshooting
            lgApiClientService.loadDeviceCapability(device.getDeviceId(), device.getModelJsonUri(), false);
            thingUID = getThingUID(device);
            thingTypeUID = getThingTypeUID(device);
        } catch (LGThinqException e) {
            logger.debug("Discovered unsupported LG device of type '{}'({}) and model '{}' with id {}",
                    device.getDeviceType(), device.getDeviceTypeId(), modelId, device.getDeviceId());
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put(DEVICE_ID, device.getDeviceId());
        properties.put(DEVICE_ALIAS, device.getAlias());
        properties.put(MODEL_URL_INFO, device.getModelJsonUri());
        properties.put(PLATFORM_TYPE, device.getPlatformType());
        properties.put(PROPERTY_MODEL_ID, modelId);

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                .withProperties(properties).withBridge(bridgeHandlerUID).withRepresentationProperty(DEVICE_ID)
                .withLabel(device.getAlias()).build();

        thingDiscovered(discoveryResult);
    }

    private ThingUID getThingUID(LGDevice device) throws LGThinqException {
        ThingTypeUID thingTypeUID = getThingTypeUID(device);
        return new ThingUID(thingTypeUID,
                Objects.requireNonNull(bridgeHandlerUID, "bridgeHandleUid should never be null here"),
                device.getDeviceId());
    }

    private ThingTypeUID getThingTypeUID(LGDevice device) throws LGThinqException {
        // Short switch, but is here because it is going to be increase after new LG Devices were added
        return switch (device.getDeviceType()) {
            case AIR_CONDITIONER -> THING_TYPE_AIR_CONDITIONER;
            case HEAT_PUMP -> THING_TYPE_HEAT_PUMP;
            case WASHERDRYER_MACHINE -> THING_TYPE_WASHING_MACHINE;
            case WASHING_TOWER -> THING_TYPE_WASHING_TOWER;
            case DRYER_TOWER -> THING_TYPE_DRYER_TOWER;
            case DRYER -> THING_TYPE_DRYER;
            case REFRIGERATOR -> THING_TYPE_FRIDGE;
            case DISH_WASHER -> THING_TYPE_DISHWASHER;
            default ->
                throw new LGThinqException(String.format("device type [%s] not supported", device.getDeviceType()));
        };
    }

    @Override
    public void dispose() {
        super.dispose();
        removeOlderResults(Instant.now().toEpochMilli(), bridgeHandlerUID);
        thingHandler.unregisterDiscoveryListener();
    }
}
