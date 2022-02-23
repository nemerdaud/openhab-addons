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
package org.openhab.binding.lgthinq.internal.handler;

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.LGThinQDeviceDynStateDescriptionProvider;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.*;
import org.openhab.binding.lgthinq.lgservices.LGThinQACApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinQACApiV1ClientServiceImpl;
import org.openhab.binding.lgthinq.lgservices.model.DevicePowerState;
import org.openhab.binding.lgthinq.lgservices.model.LGDevice;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACCapability;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACSnapshot;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACTargetTmp;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.*;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGThinQAirConditionerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinQAirConditionerHandler extends LGThinQAbstractDeviceHandler<ACCapability, ACSnapshot> {

    private final ChannelUID opModeChannelUID;
    private final ChannelUID fanSpeedChannelUID;
    @Nullable
    private ACCapability acCapability;
    private final Logger logger = LoggerFactory.getLogger(LGThinQAirConditionerHandler.class);
    @NonNullByDefault
    private final LGThinQACApiClientService lgThinqACApiClientService;
    private @Nullable ScheduledFuture<?> thingStatePollingJob;

    public LGThinQAirConditionerHandler(Thing thing,
            LGThinQDeviceDynStateDescriptionProvider stateDescriptionProvider) {
        super(thing, stateDescriptionProvider);
        lgThinqACApiClientService = lgPlatformType.equals(PLATFORM_TYPE_V1)
                ? LGThinQACApiV1ClientServiceImpl.getInstance()
                : LGThinQACApiV2ClientServiceImpl.getInstance();
        opModeChannelUID = new ChannelUID(getThing().getUID(), CHANNEL_MOD_OP_ID);
        fanSpeedChannelUID = new ChannelUID(getThing().getUID(), CHANNEL_FAN_SPEED_ID);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Thinq thing.");
        Bridge bridge = getBridge();
        initializeThing((bridge == null) ? null : bridge.getStatus());
    }

    @Override
    protected void updateDeviceChannels(ACSnapshot shot) {
        updateState(CHANNEL_MOD_OP_ID, new DecimalType(shot.getOperationMode()));
        updateState(CHANNEL_POWER_ID, OnOffType.from(shot.getPowerStatus() == DevicePowerState.DV_POWER_ON));
        // TODO - validate if is needed to change the status of the thing from OFFLINE to ONLINE (as
        // soon as LG WebOs do)
        updateState(CHANNEL_FAN_SPEED_ID, new DecimalType(shot.getAirWindStrength()));
        updateState(CHANNEL_CURRENT_TEMP_ID, new DecimalType(shot.getCurrentTemperature()));
        updateState(CHANNEL_TARGET_TEMP_ID, new DecimalType(shot.getTargetTemperature()));
    }

    @Override
    public void updateChannelDynStateDescription() throws LGThinqApiException {
        ACCapability acCap = getCapabilities();
        if (isLinked(opModeChannelUID)) {
            List<StateOption> options = new ArrayList<>();
            acCap.getSupportedOpMode().forEach((v) -> options
                    .add(new StateOption(emptyIfNull(acCap.getOpMod().get(v)), emptyIfNull(CAP_AC_OP_MODE.get(v)))));
            stateDescriptionProvider.setStateOptions(opModeChannelUID, options);
        }
        if (isLinked(fanSpeedChannelUID)) {
            List<StateOption> options = new ArrayList<>();
            acCap.getSupportedFanSpeed().forEach((v) -> options.add(
                    new StateOption(emptyIfNull(acCap.getFanSpeed().get(v)), emptyIfNull(CAP_AC_FAN_SPEED.get(v)))));
            stateDescriptionProvider.setStateOptions(fanSpeedChannelUID, options);
        }
        if (isLinked(fanSpeedChannelUID)) {
            List<StateOption> options = new ArrayList<>();
            acCap.getSupportedFanSpeed().forEach((v) -> options.add(
                    new StateOption(emptyIfNull(acCap.getFanSpeed().get(v)), emptyIfNull(CAP_AC_FAN_SPEED.get(v)))));
            stateDescriptionProvider.setStateOptions(fanSpeedChannelUID, options);
        }
    }

    @Override
    public LGThinQApiClientService<ACCapability, ACSnapshot> getLgThinQAPIClientService() {
        return lgThinqACApiClientService;
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

    @Override
    public void onDeviceAdded(LGDevice device) {
        // TODO - handle it. Think if it's needed
    }

    @Override
    public String getDeviceId() {
        return getThing().getUID().getId();
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
        switch (params.channelUID) {
            case CHANNEL_MOD_OP_ID: {
                if (params.command instanceof DecimalType) {
                    lgThinqACApiClientService.changeOperationMode(getBridgeId(), getDeviceId(),
                            ((DecimalType) command).intValue());
                } else {
                    logger.warn("Received command different of Numeric in Mod Operation. Ignoring");
                }
                break;
            }
            case CHANNEL_FAN_SPEED_ID: {
                if (command instanceof DecimalType) {
                    lgThinqACApiClientService.changeFanSpeed(getBridgeId(), getDeviceId(),
                            ((DecimalType) command).intValue());
                } else {
                    logger.warn("Received command different of Numeric in FanSpeed Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_POWER_ID: {
                if (command instanceof OnOffType) {
                    lgThinqACApiClientService.turnDevicePower(getBridgeId(), getDeviceId(),
                            command == OnOffType.ON ? DevicePowerState.DV_POWER_ON : DevicePowerState.DV_POWER_OFF);
                } else {
                    logger.warn("Received command different of OnOffType in Power Channel. Ignoring");
                }
                break;
            }
            case CHANNEL_TARGET_TEMP_ID: {
                double targetTemp;
                if (command instanceof DecimalType) {
                    targetTemp = ((DecimalType) command).doubleValue();
                } else if (command instanceof QuantityType) {
                    targetTemp = ((QuantityType<?>) command).doubleValue();
                } else {
                    logger.warn("Received command different of Numeric in TargetTemp Channel. Ignoring");
                    break;
                }
                lgThinqACApiClientService.changeTargetTemperature(getBridgeId(), getDeviceId(),
                        ACTargetTmp.statusOf(targetTemp));
                break;
            }
            default: {
                logger.error("Command {} to the channel {} not supported. Ignored.", command, params.channelUID);
            }
        }
    }
}
