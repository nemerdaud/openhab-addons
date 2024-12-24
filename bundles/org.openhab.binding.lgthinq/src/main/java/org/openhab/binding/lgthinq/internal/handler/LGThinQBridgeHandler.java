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
package org.openhab.binding.lgthinq.internal.handler;

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.THINQ_CONNECTION_DATA_FILE;
import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.THINQ_USER_DATA_FOLDER;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.LGThinQBridgeConfiguration;
import org.openhab.binding.lgthinq.internal.discovery.LGThinqDiscoveryService;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientServiceFactory;
import org.openhab.binding.lgthinq.lgservices.LGThinQApiClientServiceFactory.LGThinQGeneralApiClientService;
import org.openhab.binding.lgthinq.lgservices.api.TokenManager;
import org.openhab.binding.lgthinq.lgservices.errors.LGThinqException;
import org.openhab.binding.lgthinq.lgservices.errors.RefreshTokenException;
import org.openhab.binding.lgthinq.lgservices.model.CapabilityDefinition;
import org.openhab.binding.lgthinq.lgservices.model.LGDevice;
import org.openhab.binding.lgthinq.lgservices.model.SnapshotDefinition;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.ConfigStatusBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGThinQBridgeHandler} - connect to the LG Account and get information about the user and registered
 * devices of that user.
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinQBridgeHandler extends ConfigStatusBridgeHandler implements LGThinQBridge {

    private final Map<String, LGThinQAbstractDeviceHandler<? extends CapabilityDefinition, ? extends SnapshotDefinition>> lGDeviceRegister = new ConcurrentHashMap<>();
    private final Map<String, LGDevice> lastDevicesDiscovered = new ConcurrentHashMap<>();

    private static final LGThinqDiscoveryService DUMMY_DISCOVERY_SERVICE = new LGThinqDiscoveryService();
    static {
        var logger = LoggerFactory.getLogger(LGThinQBridgeHandler.class);
        try {
            File directory = new File(THINQ_USER_DATA_FOLDER);
            if (!directory.exists()) {
                if (!directory.mkdir()) {
                    throw new LGThinqException("Can't create directory for userdata thinq");
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to setup thinq userdata directory: {}", e.getMessage());
        }
    }
    private final Logger logger = LoggerFactory.getLogger(LGThinQBridgeHandler.class);
    private LGThinQBridgeConfiguration lgthinqConfig = new LGThinQBridgeConfiguration();
    private final TokenManager tokenManager;
    private LGThinqDiscoveryService discoveryService = DUMMY_DISCOVERY_SERVICE;
    private final @Nullable LGThinQGeneralApiClientService lgApiClient;
    private @Nullable ScheduledFuture<?> devicePollingJob;
    private final HttpClientFactory httpClientFactory;

    public LGThinQBridgeHandler(Bridge bridge, HttpClientFactory httpClientFactory) {
        super(bridge);
        this.httpClientFactory = httpClientFactory;
        tokenManager = new TokenManager(httpClientFactory.getCommonHttpClient());
        lgApiClient = LGThinQApiClientServiceFactory.newGeneralApiClientService(httpClientFactory);
        lgDevicePollingRunnable = new LGDevicePollingRunnable(bridge.getUID().getId());
    }

    public HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    final ReentrantLock pollingLock = new ReentrantLock();

    /**
     * Abstract Runnable Polling Class to schedule synchronization status of the Bridge Thing Kinds !
     */
    abstract class PollingRunnable implements Runnable {
        protected final String bridgeName;
        protected LGThinQBridgeConfiguration lgthinqConfig = new LGThinQBridgeConfiguration();

        PollingRunnable(String bridgeName) {
            this.bridgeName = bridgeName;
        }

        @Override
        public void run() {
            try {
                pollingLock.lock();
                // check if configuration file already exists
                if (tokenManager.isOauthTokenRegistered(bridgeName)) {
                    logger.debug(
                            "Token authentication process has been already done. Skip first authentication process.");
                    try {
                        tokenManager.getValidRegisteredToken(bridgeName);
                    } catch (IOException e) {
                        logger.error("Unexpected error reading LGThinq TokenFile", e);
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                                "@text/error.toke-file-corrupted");
                        return;
                    } catch (RefreshTokenException e) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                                "@text/error.toke-refresh");
                        logger.error("Error refreshing token", e);
                        return;
                    }
                } else {
                    try {
                        tokenManager.oauthFirstRegistration(bridgeName, lgthinqConfig.getLanguage(),
                                lgthinqConfig.getCountry(), lgthinqConfig.getUsername(), lgthinqConfig.getPassword(),
                                lgthinqConfig.getAlternativeServer());
                        tokenManager.getValidRegisteredToken(bridgeName);
                        logger.debug("Successful getting token from LG API");
                    } catch (IOException e) {
                        logger.debug(
                                "I/O error accessing json token configuration file. Updating Bridge Status to OFFLINE.",
                                e);
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                                "@text/error.toke-file-access-error");
                        return;
                    } catch (LGThinqException e) {
                        logger.debug("Error accessing LG API. Updating Bridge Status to OFFLINE.", e);
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "@text/error.lgapi-communication-error");
                        return;
                    }
                }
                if (thing.getStatus() != ThingStatus.ONLINE) {
                    updateStatus(ThingStatus.ONLINE);
                }

                try {
                    doConnectedRun();
                } catch (Exception e) {
                    logger.error("Unexpected error getting device list from LG account", e);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "@text/error.lgapi-getting-devices");
                }

            } finally {
                pollingLock.unlock();
            }
        }

        protected abstract void doConnectedRun() throws LGThinqException;
    }

    @Override
    public void registerDiscoveryListener(LGThinqDiscoveryService listener) {
        discoveryService = listener;
    }

    @Override
    public void registryListenerThing(
            LGThinQAbstractDeviceHandler<? extends CapabilityDefinition, ? extends SnapshotDefinition> thing) {
        if (lGDeviceRegister.get(thing.getDeviceId()) == null) {
            lGDeviceRegister.put(thing.getDeviceId(), thing);
            // remove device from discovery list, if exists.
            LGDevice device = lastDevicesDiscovered.get(thing.getDeviceId());
            if (device != null && discoveryService != DUMMY_DISCOVERY_SERVICE) {
                discoveryService.removeLgDeviceDiscovery(device);
            }
        }
    }

    @Override
    public void unRegistryListenerThing(
            LGThinQAbstractDeviceHandler<? extends CapabilityDefinition, ? extends SnapshotDefinition> thing) {
        lGDeviceRegister.remove(thing.getDeviceId());
    }

    private final LGDevicePollingRunnable lgDevicePollingRunnable;

    private LGThinQGeneralApiClientService getLgApiClient() {
        return Objects.requireNonNull(lgApiClient, "Not expected lgApiClient null. It most likely a bug");
    }

    class LGDevicePollingRunnable extends PollingRunnable {
        public LGDevicePollingRunnable(String bridgeName) {
            super(bridgeName);
        }

        @Override
        protected void doConnectedRun() throws LGThinqException {
            Map<String, LGDevice> lastDevicesDiscoveredCopy = new HashMap<>(lastDevicesDiscovered);
            List<LGDevice> devices = getLgApiClient().listAccountDevices(bridgeName);
            // if not registered yet, and not discovered before, then add to discovery list.
            devices.forEach(device -> {
                String deviceId = device.getDeviceId();
                logger.debug("Device found: {}", deviceId);
                if (lGDeviceRegister.get(deviceId) == null && !lastDevicesDiscovered.containsKey(deviceId)) {
                    logger.debug("Adding new LG Device to things registry with id:{}", deviceId);
                    if (discoveryService != DUMMY_DISCOVERY_SERVICE) {
                        discoveryService.addLgDeviceDiscovery(device);
                    }
                } else {
                    if (discoveryService != DUMMY_DISCOVERY_SERVICE && lGDeviceRegister.get(deviceId) != null) {
                        discoveryService.removeLgDeviceDiscovery(device);
                    }
                }
                lastDevicesDiscovered.put(deviceId, device);
                lastDevicesDiscoveredCopy.remove(deviceId);
            });
            // the rest in lastDevicesDiscoveredCopy is not more registered in LG API. Remove from discovery
            lastDevicesDiscoveredCopy.forEach((deviceId, device) -> {
                logger.debug("LG Device '{}' removed.", deviceId);
                lastDevicesDiscovered.remove(deviceId);

                LGThinQAbstractDeviceHandler<? extends CapabilityDefinition, ? extends SnapshotDefinition> deviceThing = lGDeviceRegister
                        .get(deviceId);
                if (deviceThing != null) {
                    deviceThing.onDeviceRemoved();
                }
                if (discoveryService != DUMMY_DISCOVERY_SERVICE && deviceThing != null) {
                    discoveryService.removeLgDeviceDiscovery(device);
                }
            });

            lGDeviceRegister.values().forEach(LGThinQAbstractDeviceHandler::refreshStatus);
        }
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        List<ConfigStatusMessage> resultList = new ArrayList<>();
        if (lgthinqConfig.username.isEmpty()) {
            resultList.add(ConfigStatusMessage.Builder.error("USERNAME").withMessageKeySuffix("missing field")
                    .withArguments("username").build());
        }
        if (lgthinqConfig.password.isEmpty()) {
            resultList.add(ConfigStatusMessage.Builder.error("PASSWORD").withMessageKeySuffix("missing field")
                    .withArguments("password").build());
        }
        if (lgthinqConfig.language.isEmpty()) {
            resultList.add(ConfigStatusMessage.Builder.error("LANGUAGE").withMessageKeySuffix("missing field")
                    .withArguments("language").build());
        }
        if (lgthinqConfig.country.isEmpty()) {
            resultList.add(ConfigStatusMessage.Builder.error("COUNTRY").withMessageKeySuffix("missing field")
                    .withArguments("country").build());

        }
        return resultList;
    }

    @Override
    @SuppressWarnings("null")
    public void handleRemoval() {
        if (devicePollingJob != null) {
            devicePollingJob.cancel(true);
        }
        tokenManager.cleanupTokenRegistry(
                Objects.requireNonNull(getBridge(), "Not expected bridge null here").getUID().getId());
        super.handleRemoval();
    }

    @Override
    @SuppressWarnings("null")
    public void dispose() {
        if (devicePollingJob != null) {
            devicePollingJob.cancel(true);
            devicePollingJob = null;
        }
    }

    @Override
    public <T> T getConfigAs(Class<T> configurationClass) {
        return super.getConfigAs(configurationClass);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing LGThinq bridge handler.");
        lgthinqConfig = getConfigAs(LGThinQBridgeConfiguration.class);
        lgDevicePollingRunnable.lgthinqConfig = lgthinqConfig;
        if (lgthinqConfig.username.isEmpty() || lgthinqConfig.password.isEmpty() || lgthinqConfig.language.isEmpty()
                || lgthinqConfig.country.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/error.mandotory-fields-missing");
        } else {
            // updateStatus(ThingStatus.UNKNOWN);
            startLGThinqDevicePolling();
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("Bridge Configuration was updated. Cleaning the token registry file");
        File f = new File(String.format(THINQ_CONNECTION_DATA_FILE, getThing().getUID().getId()));
        if (f.isFile()) {
            // file exists. Delete it
            if (!f.delete()) {
                logger.error("Unexpected error deleting file:{}", f.getAbsolutePath());
            }
        }
        super.handleConfigurationUpdate(configurationParameters);
    }

    @SuppressWarnings("null")
    private void startLGThinqDevicePolling() {
        // stop current scheduler, if any
        if (devicePollingJob != null && !devicePollingJob.isDone()) {
            devicePollingJob.cancel(true);
        }
        long poolingInterval;
        int configPollingInterval = lgthinqConfig.getPollingIntervalSec();
        // It's not recommended to polling for resources in LG API short intervals to do not enter in BlackList
        if (configPollingInterval < 300 && configPollingInterval != 0) {
            poolingInterval = TimeUnit.SECONDS.toSeconds(300);
            logger.info("Wrong configuration value for pooling interval. Using default value: {}s", poolingInterval);
        } else {
            if (configPollingInterval == 0) {
                logger.info("LG's discovery pooling disabled (configured as zero)");
                return;
            }
            poolingInterval = configPollingInterval;
        }
        // submit instantlly and schedule for the next polling interval.
        runDiscovery();
        devicePollingJob = scheduler.scheduleWithFixedDelay(lgDevicePollingRunnable, 2, poolingInterval,
                TimeUnit.SECONDS);
    }

    public void runDiscovery() {
        scheduler.submit(lgDevicePollingRunnable);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    public void unregisterDiscoveryListener() {
        discoveryService = DUMMY_DISCOVERY_SERVICE;
    }

    /**
     * Registry the OSGi services used by this Bridge.
     * Eventually, the Discovery Service will be activated with this bridge as argument.
     *
     * @return Services to be registered to OSGi.
     */
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(LGThinqDiscoveryService.class);
    }
}