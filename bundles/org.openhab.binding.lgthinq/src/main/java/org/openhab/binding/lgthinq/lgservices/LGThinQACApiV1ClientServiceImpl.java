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
package org.openhab.binding.lgthinq.lgservices;

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.*;

import java.io.IOException;
import java.util.*;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.api.RestResult;
import org.openhab.binding.lgthinq.internal.api.RestUtils;
import org.openhab.binding.lgthinq.internal.api.TokenResult;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.model.DevicePowerState;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACCapability;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACSnapshot;
import org.openhab.binding.lgthinq.lgservices.model.ac.ACTargetTmp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGThinQACApiV1ClientServiceImpl}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinQACApiV1ClientServiceImpl extends LGThinQAbstractApiClientService<ACCapability, ACSnapshot>
        implements LGThinQACApiClientService {
    private static final LGThinQACApiClientService instance;
    private static final Logger logger = LoggerFactory.getLogger(LGThinQACApiV1ClientServiceImpl.class);

    static {
        instance = new LGThinQACApiV1ClientServiceImpl(ACCapability.class, ACSnapshot.class);
    }

    protected LGThinQACApiV1ClientServiceImpl(Class<ACCapability> capabilityClass, Class<ACSnapshot> snapshotClass) {
        super(capabilityClass, snapshotClass);
    }

    @Override
    protected void beforeGetDataDevice(@NonNull String bridgeName, @NonNull String deviceId) {
        // Nothing to do on V1 AC here
    }

    public static LGThinQACApiClientService getInstance() {
        return instance;
    }

    /**
     * Get snapshot data from the device.
     * <b>It works only for API V2 device versions!</b>
     * 
     * @param deviceId device ID for de desired V2 LG Thinq.
     * @return return map containing metamodel of settings and snapshot
     * @throws LGThinqApiException if some communication error occur.
     */
    @Override
    @Nullable
    public ACSnapshot getDeviceData(@NonNull String bridgeName, @NonNull String deviceId) throws LGThinqApiException {
        throw new UnsupportedOperationException("Method not supported in V1 API device.");
    }

    @Override
    public double getInstantPowerConsumption(@NonNull String bridgeName, @NonNull String deviceId)
            throws LGThinqApiException, IOException {
        try {
            RestResult resp = getConfigCommands(bridgeName, deviceId, "OutTotalInstantPower");
            Map<String, Object> result = handleV1GenericErrorResult(resp);
            return 0;
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting jet mode", e);
        }
    }

    private RestResult sendControlCommands(String bridgeName, String deviceId, String keyName, int value)
            throws Exception {
        TokenResult token = tokenManager.getValidRegisteredToken(bridgeName);
        UriBuilder builder = UriBuilder.fromUri(token.getGatewayInfo().getApiRootV1()).path(V1_CONTROL_OP);
        Map<String, String> headers = getCommonHeaders(token.getGatewayInfo().getLanguage(),
                token.getGatewayInfo().getCountry(), token.getAccessToken(), token.getUserInfo().getUserNumber());

        String payload = String.format(
                "{\n" + "   \"lgedmRoot\":{\n" + "      \"cmd\": \"Control\"," + "      \"cmdOpt\": \"Set\","
                        + "      \"value\": {\"%s\": \"%d\"}," + "      \"deviceId\": \"%s\","
                        + "      \"workId\": \"%s\"," + "      \"data\": \"\"" + "   }\n" + "}",
                keyName, value, deviceId, UUID.randomUUID().toString());
        return RestUtils.postCall(builder.build().toURL().toString(), headers, payload);
    }

    private RestResult getConfigCommands(String bridgeName, String deviceId, String keyName) throws Exception {
        TokenResult token = tokenManager.getValidRegisteredToken(bridgeName);
        UriBuilder builder = UriBuilder.fromUri(token.getGatewayInfo().getApiRootV1()).path(V1_CONTROL_OP);
        Map<String, String> headers = getCommonHeaders(token.getGatewayInfo().getLanguage(),
                token.getGatewayInfo().getCountry(), token.getAccessToken(), token.getUserInfo().getUserNumber());

        String payload = String.format("{\n" + "   \"lgedmRoot\":{\n" + "      \"cmd\": \"Config\","
                + "      \"cmdOpt\": \"Get\"," + "      \"value\": \"%s\"," + "      \"deviceId\": \"%s\","
                + "      \"workId\": \"%s\"," + "      \"data\": \"\"" + "   }\n" + "}", keyName, deviceId,
                UUID.randomUUID().toString());
        return RestUtils.postCall(builder.build().toURL().toString(), headers, payload);
    }

    @Override
    public void turnDevicePower(String bridgeName, String deviceId, DevicePowerState newPowerState)
            throws LGThinqApiException {
        try {
            RestResult resp = sendControlCommands(bridgeName, deviceId, "Operation", newPowerState.commandValue());
            handleV1GenericErrorResult(resp);
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting device power", e);
        }
    }

    @Override
    public void turnCoolJetMode(String bridgeName, String deviceId, String modeOnOff) throws LGThinqApiException {
        turnGenericMode(bridgeName, deviceId, "Jet", modeOnOff);
    }

    public void turnAirCleanMode(String bridgeName, String deviceId, String modeOnOff) throws LGThinqApiException {
        turnGenericMode(bridgeName, deviceId, "AirClean", modeOnOff);
    }

    public void turnAutoDryMode(String bridgeName, String deviceId, String modeOnOff) throws LGThinqApiException {
        turnGenericMode(bridgeName, deviceId, "AutoDry", modeOnOff);
    }

    public void turnEnergySavingMode(String bridgeName, String deviceId, String modeOnOff) throws LGThinqApiException {
        turnGenericMode(bridgeName, deviceId, "PowerSave", modeOnOff);
    }

    protected void turnGenericMode(String bridgeName, String deviceId, String modeName, String modeOnOff)
            throws LGThinqApiException {
        try {
            RestResult resp = sendControlCommands(bridgeName, deviceId, modeName, Integer.parseInt(modeOnOff));
            handleV1GenericErrorResult(resp);
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting " + modeName + " mode", e);
        }
    }

    @Override
    public void changeOperationMode(String bridgeName, String deviceId, int newOpMode) throws LGThinqApiException {
        try {
            RestResult resp = sendControlCommands(bridgeName, deviceId, "OpMode", newOpMode);

            handleV1GenericErrorResult(resp);
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting operation mode", e);
        }
    }

    @Override
    public void changeFanSpeed(String bridgeName, String deviceId, int newFanSpeed) throws LGThinqApiException {
        try {
            RestResult resp = sendControlCommands(bridgeName, deviceId, "WindStrength", newFanSpeed);

            handleV1GenericErrorResult(resp);
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting fan speed", e);
        }
    }

    @Override
    public void changeTargetTemperature(String bridgeName, String deviceId, ACTargetTmp newTargetTemp)
            throws LGThinqApiException {
        try {
            RestResult resp = sendControlCommands(bridgeName, deviceId, "TempCfg", newTargetTemp.commandValue());

            handleV1GenericErrorResult(resp);
        } catch (Exception e) {
            throw new LGThinqApiException("Error adjusting target temperature", e);
        }
    }
}
