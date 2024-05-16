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
package org.openhab.binding.lgthinq.internal.handler;

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.LGThinQDeviceDynStateDescriptionProvider;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinQFridgeApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinQFridgeApiV1ClientServiceImpl;
import org.openhab.binding.lgthinq.lgservices.LGThinQFridgeApiV2ClientServiceImpl;
import org.openhab.binding.lgthinq.lgservices.model.DeviceTypes;
import org.openhab.binding.lgthinq.lgservices.model.LGDevice;
import org.openhab.binding.lgthinq.lgservices.model.devices.fridge.FridgeCanonicalSnapshot;
import org.openhab.binding.lgthinq.lgservices.model.devices.fridge.FridgeCapability;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGThinQFridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Nemer Daud - Initial contribution
 * @author Arne Seime - Complementary sensors
 */
@NonNullByDefault
public class LGThinQFridgeHandler extends LGThinQAbstractDeviceHandler<FridgeCapability, FridgeCanonicalSnapshot> {

    private final ChannelUID fridgeTempChannelUID;
    private final ChannelUID freezerTempChannelUID;
    private String tempUnit = TEMP_UNIT_CELSIUS;
    private final Logger logger = LoggerFactory.getLogger(LGThinQFridgeHandler.class);
    @NonNullByDefault
    private final LGThinQFridgeApiClientService lgThinqFridgeApiClientService;
    private @Nullable ScheduledFuture<?> thingStatePollingJob;

    public LGThinQFridgeHandler(Thing thing, LGThinQDeviceDynStateDescriptionProvider stateDescriptionProvider) {
        super(thing, stateDescriptionProvider);
        lgThinqFridgeApiClientService = lgPlatformType.equals(PLATFORM_TYPE_V1)
                ? LGThinQFridgeApiV1ClientServiceImpl.getInstance()
                : LGThinQFridgeApiV2ClientServiceImpl.getInstance();
        fridgeTempChannelUID = new ChannelUID(getThing().getUID(), CHANNEL_FRIDGE_TEMP_ID);
        freezerTempChannelUID = new ChannelUID(getThing().getUID(), CHANNEL_FREEZER_TEMP_ID);
        ChannelUID doorChannelUID = new ChannelUID(getThing().getUID(), FR_CHANNEL_DOOR_ID);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Thinq thing.");
        Bridge bridge = getBridge();
        initializeThing((bridge == null) ? null : bridge.getStatus());
    }

    @Override
    protected void updateDeviceChannels(FridgeCanonicalSnapshot shot) {
        updateState(CHANNEL_FRIDGE_TEMP_ID, new QuantityType<Temperature>(shot.getFridgeStrTemp()));
        updateState(CHANNEL_FREEZER_TEMP_ID, new QuantityType<Temperature>(shot.getFreezerStrTemp()));
        updateState(FR_CHANNEL_DOOR_ID, parseDoorStatus(shot.getDoorStatus()));

        updateState(CHANNEL_REF_TEMP_UNIT, new StringType(shot.getTempUnit()));
        if (!tempUnit.equals(shot.getTempUnit())) {
            tempUnit = shot.getTempUnit();
            try {
                // force update states after first snapshot fetched to fit changes in temperature unit
                updateChannelDynStateDescription();
            } catch (Exception ex) {
                logger.error("Error updating dynamic state description", ex);
            }
        }
    }

    private State parseDoorStatus(String doorStatus) {
        if ("CLOSE".equals(doorStatus)) {
            return OpenClosedType.CLOSED;
        } else if ("OPEN".equals(doorStatus)) {
            return OpenClosedType.OPEN;
        } else {
            return UnDefType.UNDEF;
        }
    }

    @Override
    public void updateChannelDynStateDescription() throws LGThinqApiException {
        FridgeCapability refCap = getCapabilities();
        // temperature channels are little different. First we need to get the tempUnit in the first snapshot,

        if (isLinked(fridgeTempChannelUID)) {
            updateTemperatureChannel(fridgeTempChannelUID,
                    TEMP_UNIT_CELSIUS.equals(tempUnit) ? refCap.getFridgeTempCMap() : refCap.getFridgeTempFMap());
        }
        if (isLinked(freezerTempChannelUID)) {
            updateTemperatureChannel(freezerTempChannelUID,
                    TEMP_UNIT_CELSIUS.equals(tempUnit) ? refCap.getFreezerTempCMap() : refCap.getFreezerTempFMap());
        }
    }

    private void updateTemperatureChannel(ChannelUID tempChannelUID, Map<String, String> mapOptions) {
        List<StateOption> options = new ArrayList<>();
        mapOptions.forEach((value, label) -> options.add(new StateOption(value, label)));
        stateDescriptionProvider.setStatePattern(tempChannelUID,
                "%.0f " + (TEMP_UNIT_CELSIUS.equals(tempUnit) ? TEMP_UNIT_CELSIUS_SYMBOL
                        : (TEMP_UNIT_FAHRENHEIT.equals(tempUnit) ? TEMP_UNIT_FAHRENHEIT_SYMBOL : "%unit%")));
        stateDescriptionProvider.setStateOptions(tempChannelUID, options);
    }

    @Override
    public LGThinQApiClientService<FridgeCapability, FridgeCanonicalSnapshot> getLgThinQAPIClientService() {
        return lgThinqFridgeApiClientService;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    protected void stopThingStatePolling() {
        if (thingStatePollingJob != null && !thingStatePollingJob.isDone()) {
            logger.debug("Stopping LG thinq polling for device/alias: {}/{}", getDeviceId(), getDeviceAlias());
            thingStatePollingJob.cancel(true);
        }
    }

    protected DeviceTypes getDeviceType() {
        return DeviceTypes.AIR_CONDITIONER;
    }

    @Override
    public void onDeviceAdded(LGDevice device) {
        // TODO - handle it. Think if it's needed
    }

    @Override
    public String getDeviceAlias() {
        return emptyIfNull(getThing().getProperties().get(DEVICE_ALIAS));
    }

    @Override
    public String getDeviceUriJsonConfig() {
        return emptyIfNull(getThing().getProperties().get(MODEL_URL_INFO));
    }

    @Override
    public void onDeviceRemoved() {
        // TODO - HANDLE IT, Think if it's needed
    }

    @Override
    public void onDeviceDisconnected() {
        // TODO - HANDLE IT, Think if it's needed
    }

    protected void processCommand(AsyncCommandParams params) throws LGThinqApiException {
        Command command = params.command;
        // TODO - Implement commands
    }
}
