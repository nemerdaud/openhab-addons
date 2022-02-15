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

import java.util.*;
import java.util.concurrent.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.LGThinQDeviceDynStateDescriptionProvider;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinQDRApiClientService;
import org.openhab.binding.lgthinq.lgservices.LGThinQDRApiV2ClientServiceImpl;
import org.openhab.binding.lgthinq.lgservices.model.DevicePowerState;
import org.openhab.binding.lgthinq.lgservices.model.LGDevice;
import org.openhab.binding.lgthinq.lgservices.model.dryer.DryerCapability;
import org.openhab.binding.lgthinq.lgservices.model.dryer.DryerSnapshot;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.*;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGThinQDryerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinQDryerHandler extends LGThinQAbstractDeviceHandler<DryerCapability, DryerSnapshot> {

    @Nullable
    private DryerCapability dryerCapability;
    private final ChannelUID stateChannelUUID;
    private final ChannelUID processStateChannelUUID;
    private final ChannelUID dryLevelChannelUUID;
    private final ChannelUID errorChannelUUID;
    private final ChannelUID courseChannelUUID;
    private final ChannelUID smartCourseChannelUUID;

    private final Logger logger = LoggerFactory.getLogger(LGThinQDryerHandler.class);
    @NonNullByDefault
    private final LGThinQDRApiClientService lgThinqDRApiClientService;
    // Bridges status that this thing must top scanning for state change
    private static final Set<ThingStatusDetail> BRIDGE_STATUS_DETAIL_ERROR = Set.of(ThingStatusDetail.BRIDGE_OFFLINE,
            ThingStatusDetail.BRIDGE_UNINITIALIZED, ThingStatusDetail.COMMUNICATION_ERROR,
            ThingStatusDetail.CONFIGURATION_ERROR);
    private @Nullable ScheduledFuture<?> thingStatePollingJob;
    private @Nullable Future<?> commandExecutorQueueJob;

    public LGThinQDryerHandler(Thing thing, LGThinQDeviceDynStateDescriptionProvider stateDescriptionProvider) {
        super(thing, stateDescriptionProvider);
        lgThinqDRApiClientService = LGThinQDRApiV2ClientServiceImpl.getInstance();
        stateChannelUUID = new ChannelUID(getThing().getUID(), DR_CHANNEL_STATE_ID);
        courseChannelUUID = new ChannelUID(getThing().getUID(), DR_CHANNEL_COURSE_ID);
        smartCourseChannelUUID = new ChannelUID(getThing().getUID(), DR_CHANNEL_SMART_COURSE_ID);
        processStateChannelUUID = new ChannelUID(getThing().getUID(), DR_CHANNEL_PROCESS_STATE_ID);
        errorChannelUUID = new ChannelUID(getThing().getUID(), DR_CHANNEL_ERROR_ID);
        dryLevelChannelUUID = new ChannelUID(getThing().getUID(), DR_CHANNEL_DRY_LEVEL_ID);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Thinq thing.");
        Bridge bridge = getBridge();
        initializeThing((bridge == null) ? null : bridge.getStatus());
    }

    @Override
    protected void updateDeviceChannels(DryerSnapshot shot) {
        updateState(CHANNEL_POWER_ID, OnOffType.from(shot.getPowerStatus() == DevicePowerState.DV_POWER_ON));
        updateState(DR_CHANNEL_STATE_ID, new StringType(shot.getState()));
        updateState(DR_CHANNEL_COURSE_ID, new StringType(shot.getCourse()));
        updateState(DR_CHANNEL_SMART_COURSE_ID, new StringType(shot.getSmartCourse()));
        updateState(DR_CHANNEL_PROCESS_STATE_ID, new StringType(shot.getProcessState()));
        updateState(DR_CHANNEL_CHILD_LOCK_ID, new StringType(shot.getChildLock()));
        updateState(DR_CHANNEL_REMAIN_TIME_ID, new StringType(shot.getRemainingTime()));
        updateState(DR_CHANNEL_DRY_LEVEL_ID, new StringType(shot.getDryLevel()));
        updateState(DR_CHANNEL_ERROR_ID, new StringType(shot.getError()));
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    protected void processCommand(LGThinQAbstractDeviceHandler.AsyncCommandParams params) throws LGThinqApiException {
        Command command = params.command;
        switch (params.channelUID) {
            case CHANNEL_POWER_ID: {
                if (command instanceof OnOffType) {
                    lgThinqDRApiClientService.turnDevicePower(getBridgeId(), getDeviceId(),
                            command == OnOffType.ON ? DevicePowerState.DV_POWER_ON : DevicePowerState.DV_POWER_OFF);
                } else {
                    logger.warn("Received command different of OnOffType in Power Channel. Ignoring");
                }
                break;
            }
            default: {
                logger.error("Command {} to the channel {} not supported. Ignored.", command, params.channelUID);
            }
        }
    }

    @Override
    public void updateChannelDynStateDescription() throws LGThinqApiException {
        DryerCapability drCap = getCapabilities();
        if (isLinked(stateChannelUUID)) {
            List<StateOption> options = new ArrayList<>();
            // invert key/value
            drCap.getState().forEach((k, v) -> options.add(new StateOption(k, keyIfValueNotFound(CAP_DR_STATE, v))));
            stateDescriptionProvider.setStateOptions(stateChannelUUID, options);
        }
        if (isLinked(courseChannelUUID)) {
            List<StateOption> options = new ArrayList<>();
            drCap.getCourses().forEach((k, v) -> options.add(new StateOption(k, emptyIfNull(v))));
            stateDescriptionProvider.setStateOptions(courseChannelUUID, options);
        }
        if (isLinked(smartCourseChannelUUID)) {
            List<StateOption> options = new ArrayList<>();
            drCap.getSmartCourses().forEach((k, v) -> options.add(new StateOption(k, emptyIfNull(v))));
            stateDescriptionProvider.setStateOptions(smartCourseChannelUUID, options);
        }
        if (isLinked(processStateChannelUUID)) {
            List<StateOption> options = new ArrayList<>();
            drCap.getProcessStates()
                    .forEach((k, v) -> options.add(new StateOption(k, keyIfValueNotFound(CAP_DR_PROCESS_STATE, v))));
            stateDescriptionProvider.setStateOptions(processStateChannelUUID, options);
        }
        if (isLinked(errorChannelUUID)) {
            List<StateOption> options = new ArrayList<>();
            drCap.getErrors().forEach((k, v) -> options.add(new StateOption(k, emptyIfNull(v))));
            stateDescriptionProvider.setStateOptions(errorChannelUUID, options);
        }
        if (isLinked(dryLevelChannelUUID)) {
            List<StateOption> options = new ArrayList<>();
            drCap.getDryLevels()
                    .forEach((k, v) -> options.add(new StateOption(k, keyIfValueNotFound(CAP_DR_DRY_LEVEL, v))));
            stateDescriptionProvider.setStateOptions(dryLevelChannelUUID, options);
        }
    }

    @Override
    public LGThinQApiClientService<DryerCapability, DryerSnapshot> getLgThinQAPIClientService() {
        return lgThinqDRApiClientService;
    }

    @Override
    protected Logger getLogger() {
        return logger;
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
    public String getDeviceModelName() {
        return emptyIfNull(getThing().getProperties().get(MODEL_NAME));
    }

    @Override
    public String getDeviceUriJsonConfig() {
        return emptyIfNull(getThing().getProperties().get(MODEL_URL_INFO));
    }

    @Override
    public boolean onDeviceStateChanged() {
        // TODO - HANDLE IT, Think if it's needed
        return false;
    }

    @Override
    public void onDeviceRemoved() {
        // TODO - HANDLE IT, Think if it's needed
    }

    @Override
    public void onDeviceGone() {
        // TODO - HANDLE IT, Think if it's needed
    }
}
