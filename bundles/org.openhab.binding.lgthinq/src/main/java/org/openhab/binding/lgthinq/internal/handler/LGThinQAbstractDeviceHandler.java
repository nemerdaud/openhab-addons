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

import java.lang.reflect.ParameterizedType;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.LGThinQDeviceDynStateDescriptionProvider;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqDeviceV1MonitorExpiredException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqDeviceV1OfflineException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqException;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientService;
import org.openhab.binding.lgthinq.lgservices.model.*;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;

/**
 * The {@link LGThinQAbstractDeviceHandler} is a main interface contract for all LG Thinq things
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public abstract class LGThinQAbstractDeviceHandler<C extends Capability, S extends Snapshot> extends BaseThingHandler {

    protected final String lgPlatformType;
    private final Class<S> snapshotClass;
    @Nullable
    protected C thinQCapability;
    private @Nullable Future<?> commandExecutorQueueJob;
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private @Nullable ScheduledFuture<?> thingStatePollingJob;
    private final ScheduledExecutorService pollingScheduler = Executors.newScheduledThreadPool(1);
    private boolean monitorV1Began = false;
    private String monitorWorkId = "";
    protected final LinkedBlockingQueue<AsyncCommandParams> commandBlockQueue = new LinkedBlockingQueue<>(20);
    private String bridgeId = "";
    private ThingStatus lastThingStatus = ThingStatus.UNKNOWN;
    // Bridges status that this thing must top scanning for state change
    private static final Set<ThingStatusDetail> BRIDGE_STATUS_DETAIL_ERROR = Set.of(ThingStatusDetail.BRIDGE_OFFLINE,
            ThingStatusDetail.BRIDGE_UNINITIALIZED, ThingStatusDetail.COMMUNICATION_ERROR,
            ThingStatusDetail.CONFIGURATION_ERROR);

    protected final LGThinQDeviceDynStateDescriptionProvider stateDescriptionProvider;

    public LGThinQAbstractDeviceHandler(Thing thing,
            LGThinQDeviceDynStateDescriptionProvider stateDescriptionProvider) {
        super(thing);
        this.stateDescriptionProvider = stateDescriptionProvider;
        lgPlatformType = "" + thing.getProperties().get(PLATFORM_TYPE);
        this.snapshotClass = (Class<S>) ((ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[1];
    }

    protected static class AsyncCommandParams {
        final String channelUID;
        final Command command;

        public AsyncCommandParams(String channelUUID, Command command) {
            this.channelUID = channelUUID;
            this.command = command;
        }
    }

    protected void startCommandExecutorQueueJob() {
        if (commandExecutorQueueJob == null || commandExecutorQueueJob.isDone()) {
            commandExecutorQueueJob = getExecutorService().submit(getQueuedCommandExecutor());
        }
    }

    protected void stopCommandExecutorQueueJob() {
        if (commandExecutorQueueJob != null) {
            commandExecutorQueueJob.cancel(true);
        }
    }

    protected void handleStatusChanged(ThingStatus newStatus, ThingStatusDetail statusDetail) {
        if (lastThingStatus != ThingStatus.ONLINE && newStatus == ThingStatus.ONLINE) {
            // start the thing polling
            startThingStatePolling();
        } else if (lastThingStatus == ThingStatus.ONLINE && newStatus == ThingStatus.OFFLINE
                && BRIDGE_STATUS_DETAIL_ERROR.contains(statusDetail)) {
            // comunication error is not a specific Bridge error, then we must analise it to give
            // this thinq the change to recovery from communication errors
            if (statusDetail != ThingStatusDetail.COMMUNICATION_ERROR
                    || (getBridge() != null && getBridge().getStatus() != ThingStatus.ONLINE)) {
                // in case of status offline, I only stop the polling if is not an COMMUNICATION_ERROR or if
                // the bridge is out
                stopThingStatePolling();
            }

        }
        lastThingStatus = newStatus;
    }

    @Override
    protected void updateStatus(ThingStatus newStatus, ThingStatusDetail statusDetail, @Nullable String description) {
        handleStatusChanged(newStatus, statusDetail);
        super.updateStatus(newStatus, statusDetail, description);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateThingStateFromLG();
        } else {
            AsyncCommandParams params = new AsyncCommandParams(channelUID.getId(), command);
            try {
                // Ensure commands are send in a pipe per device.
                commandBlockQueue.add(params);
            } catch (IllegalStateException ex) {
                getLogger().error(
                        "Device's command queue reached the size limit. Probably the device is busy ou stuck. Ignoring command.");
                updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Device Command Queue is Busy");
            }

        }
    }

    protected ExecutorService getExecutorService() {
        return executorService;
    }

    public abstract void onDeviceAdded(@NonNullByDefault LGDevice device);

    public abstract String getDeviceId();

    public abstract String getDeviceAlias();

    public abstract String getDeviceModelName();

    public abstract String getDeviceUriJsonConfig();

    public abstract boolean onDeviceStateChanged();

    public abstract void onDeviceRemoved();

    public abstract void onDeviceGone();

    public abstract void updateChannelDynStateDescription() throws LGThinqApiException;

    public abstract LGThinQApiClientService<C, S> getLgThinQAPIClientService();

    public C getCapabilities() throws LGThinqApiException {
        if (thinQCapability == null) {
            thinQCapability = getLgThinQAPIClientService().getCapability(getDeviceId(), getDeviceUriJsonConfig(),
                    false);
        }
        return Objects.requireNonNull(thinQCapability, "Unexpected error. Return of capability shouldn't ever be null");
    }

    protected abstract Logger getLogger();

    protected void initializeThing(@Nullable ThingStatus bridgeStatus) {
        getLogger().debug("initializeThing LQ Thinq {}. Bridge status {}", getThing().getUID(), bridgeStatus);
        String deviceId = getThing().getUID().getId();

        Bridge bridge = getBridge();
        if (!deviceId.isBlank()) {
            try {
                updateChannelDynStateDescription();
            } catch (LGThinqApiException e) {
                getLogger().error(
                        "Error updating channels dynamic options descriptions based on capabilities of the device. Fallback to default values.",
                        e);
            }
            if (bridge != null) {
                LGThinQBridgeHandler handler = (LGThinQBridgeHandler) bridge.getHandler();
                // registry this thing to the bridge
                if (handler == null) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
                } else {
                    handler.registryListenerThing(this);
                    if (bridgeStatus == ThingStatus.ONLINE) {
                        updateStatus(ThingStatus.ONLINE);
                    } else {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                    }
                }
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error-no-device-id");
        }
        // finally, start command queue, regardless of the thing state, as we can still try to send commands without
        // property ONLINE (the successful result from command request can put the thing in ONLINE status).
        startCommandExecutorQueueJob();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        getLogger().debug("bridgeStatusChanged {}", bridgeStatusInfo);
        super.bridgeStatusChanged(bridgeStatusInfo);
        // restart scheduler
        initializeThing(bridgeStatusInfo.getStatus());
    }

    protected void updateThingStateFromLG() {
        try {
            @Nullable
            S shot = getSnapshotDeviceAdapter(getDeviceId());
            if (shot == null) {
                // no data to update. Maybe, the monitor stopped, then it gonna be restarted next try.
                return;
            }
            if (!shot.isOnline()) {
                if (getThing().getStatus() != ThingStatus.OFFLINE) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE);
                    updateState(CHANNEL_POWER_ID,
                            OnOffType.from(shot.getPowerStatus() == DevicePowerState.DV_POWER_OFF));
                }
                return;
            }
            updateDeviceChannels(shot);

        } catch (LGThinqException e) {
            getLogger().error("Error updating thing {}/{} from LG API. Thing goes OFFLINE until next retry.",
                    getDeviceAlias(), getDeviceId(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    protected abstract void updateDeviceChannels(S snapshot);

    protected void stopThingStatePolling() {
        if (thingStatePollingJob != null && !thingStatePollingJob.isDone()) {
            getLogger().debug("Stopping LG thinq polling for device/alias: {}/{}", getDeviceId(), getDeviceAlias());
            thingStatePollingJob.cancel(true);
        }
    }

    protected void startThingStatePolling() {
        if (thingStatePollingJob == null || thingStatePollingJob.isDone()) {
            thingStatePollingJob = pollingScheduler.scheduleWithFixedDelay(this::updateThingStateFromLG, 10,
                    DEFAULT_STATE_POLLING_UPDATE_DELAY, TimeUnit.SECONDS);
        }
    }

    private void forceStopDeviceV1Monitor(String deviceId) {
        try {
            monitorV1Began = false;
            getLgThinQAPIClientService().stopMonitor(getBridgeId(), deviceId, monitorWorkId);
        } catch (Exception e) {
            getLogger().error("Error stopping LG Device monitor", e);
        }
    }

    protected String getBridgeId() {
        if (bridgeId.isBlank() && getBridge() == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            getLogger().error("Configuration error um Thinq Thing - No Bridge defined for the thing.");
            return "UNKNOWN";
        } else if (bridgeId.isBlank() && getBridge() != null) {
            bridgeId = getBridge().getUID().getId();
        }
        return bridgeId;
    }

    @Nullable
    protected S getSnapshotDeviceAdapter(String deviceId) throws LGThinqApiException {
        // analise de platform version
        if (PLATFORM_TYPE_V2.equals(lgPlatformType)) {
            return getLgThinQAPIClientService().getDeviceData(getBridgeId(), getDeviceId());
        } else {
            try {
                if (!monitorV1Began) {
                    monitorWorkId = getLgThinQAPIClientService().startMonitor(getBridgeId(), getDeviceId());
                    monitorV1Began = true;
                }
            } catch (LGThinqDeviceV1OfflineException e) {
                forceStopDeviceV1Monitor(deviceId);
                try {
                    S shot = snapshotClass.getDeclaredConstructor().newInstance();
                    shot.setOnline(false);
                    return shot;
                } catch (Exception ex) {
                    getLogger().error("Unexpected error. Can't find default constructor for the Snapshot subclass", ex);
                    return null;
                }

            } catch (Exception e) {
                forceStopDeviceV1Monitor(deviceId);
                throw new LGThinqApiException("Error starting device monitor in LG API for the device:" + deviceId, e);
            }
            int retries = 10;
            @Nullable
            S shot;
            while (retries > 0) {
                // try to get monitoring data result 3 times.
                try {
                    shot = getLgThinQAPIClientService().getMonitorData(getBridgeId(), deviceId, monitorWorkId,
                            DeviceTypes.AIR_CONDITIONER);
                    if (shot != null) {
                        return shot;
                    }
                    Thread.sleep(500);
                    retries--;
                } catch (LGThinqDeviceV1MonitorExpiredException e) {
                    forceStopDeviceV1Monitor(deviceId);
                    getLogger().info("Monitor for device {} was expired. Forcing stop and start to next cycle.",
                            deviceId);
                    return null;
                } catch (Exception e) {
                    // If it can't get monitor handler, then stop monitor and restart the process again in new
                    // interaction
                    // Force restart monitoring because of the errors returned (just in case)
                    forceStopDeviceV1Monitor(deviceId);
                    throw new LGThinqApiException("Error getting monitor data for the device:" + deviceId, e);
                }
            }
            forceStopDeviceV1Monitor(deviceId);
            throw new LGThinqApiException("Exhausted trying to get monitor data for the device:" + deviceId);
        }
    }

    protected abstract void processCommand(AsyncCommandParams params) throws LGThinqApiException;

    protected Runnable getQueuedCommandExecutor() {
        return queuedCommandExecutor;
    }

    private final Runnable queuedCommandExecutor = () -> {
        while (true) {
            AsyncCommandParams params;
            try {
                params = commandBlockQueue.take();
            } catch (InterruptedException e) {
                getLogger().debug("Interrupting async command queue executor.");
                return;
            }

            try {
                processCommand(params);
            } catch (LGThinqException e) {
                getLogger().error("Error executing Command {} to the channel {}. Thing goes offline until retry",
                        params.command, params.channelUID, e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    };

    @Override
    public void dispose() {
        if (thingStatePollingJob != null) {
            thingStatePollingJob.cancel(true);
            stopThingStatePolling();
            stopCommandExecutorQueueJob();
            thingStatePollingJob = null;
        }
    }
}
