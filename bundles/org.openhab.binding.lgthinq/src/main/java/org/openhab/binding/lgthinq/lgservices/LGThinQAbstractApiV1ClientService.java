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
package org.openhab.binding.lgthinq.lgservices;

import static org.openhab.binding.lgthinq.internal.LGThinQBindingConstants.V1_CONTROL_OP;

import java.util.*;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.lgthinq.internal.api.RestResult;
import org.openhab.binding.lgthinq.internal.api.RestUtils;
import org.openhab.binding.lgthinq.internal.api.TokenResult;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.internal.errors.LGThinqDeviceV1OfflineException;
import org.openhab.binding.lgthinq.lgservices.model.AbstractSnapshotDefinition;
import org.openhab.binding.lgthinq.lgservices.model.CapabilityDefinition;
import org.openhab.binding.lgthinq.lgservices.model.CommandDefinition;
import org.openhab.binding.lgthinq.lgservices.model.ResultCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The {@link LGThinQAbstractApiV1ClientService}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public abstract class LGThinQAbstractApiV1ClientService<C extends CapabilityDefinition, S extends AbstractSnapshotDefinition>
        extends LGThinQAbstractApiClientService<C, S> {
    private static final Logger logger = LoggerFactory.getLogger(LGThinQAbstractApiV1ClientService.class);

    protected LGThinQAbstractApiV1ClientService(Class<C> capabilityClass, Class<S> snapshotClass,
            HttpClient httpClient) {
        super(capabilityClass, snapshotClass, httpClient);
    }

    @Override
    protected RestResult sendCommand(String bridgeName, String deviceId, String controlPath, String controlKey,
            String command, String keyName, String value) throws Exception {
        return sendCommand(bridgeName, deviceId, controlPath, controlKey, command, keyName, value, null);
    }

    protected RestResult sendCommand(String bridgeName, String deviceId, String controlPath, String controlKey,
            String command, @Nullable String keyName, @Nullable String value, @Nullable ObjectNode extraNode)
            throws Exception {
        ObjectNode payloadNode = JsonNodeFactory.instance.objectNode();
        payloadNode.put("cmd", controlKey).put("cmdOpt", command);
        if (keyName == null || keyName.isEmpty()) {
            // value is a simple text
            payloadNode.put("value", value);
        } else {
            payloadNode.putObject("value").put(keyName, value);
        }
        // String payload = String.format(
        // "{\n" + " \"lgedmRoot\":{\n" + " \"cmd\": \"%s\"," + " \"cmdOpt\": \"%s\","
        // + " \"value\": {\"%s\": \"%s\"}," + " \"deviceId\": \"%s\","
        // + " \"workId\": \"%s\"," + " \"data\": \"\"" + " }\n" + "}",
        // controlKey, command, keyName, value, deviceId, UUID.randomUUID());
        if (extraNode != null) {
            payloadNode.setAll(extraNode);
        }
        return sendCommand(bridgeName, deviceId, payloadNode);
    }

    protected RestResult sendCommand(String bridgeName, String deviceId, Object cmdPayload) throws Exception {
        TokenResult token = tokenManager.getValidRegisteredToken(bridgeName);
        UriBuilder builder = UriBuilder.fromUri(token.getGatewayInfo().getApiRootV1()).path(V1_CONTROL_OP);
        Map<String, String> headers = getCommonHeaders(token.getGatewayInfo().getLanguage(),
                token.getGatewayInfo().getCountry(), token.getAccessToken(), token.getUserInfo().getUserNumber());
        ObjectNode payloadNode = null;
        if (cmdPayload instanceof ObjectNode) {
            payloadNode = ((ObjectNode) cmdPayload).deepCopy();
        } else {
            payloadNode = objectMapper.convertValue(cmdPayload, ObjectNode.class);
        }
        ObjectNode rootNode = JsonNodeFactory.instance.objectNode();
        ObjectNode bodyNode = JsonNodeFactory.instance.objectNode();
        bodyNode.put("deviceId", deviceId);
        bodyNode.put("workId", UUID.randomUUID().toString());
        bodyNode.setAll(payloadNode);
        rootNode.set("lgedmRoot", bodyNode);
        String url = builder.build().toURL().toString();
        logger.debug("URL: {}, Post Payload:[{}]", url, rootNode.toPrettyString());
        RestResult resp = RestUtils.postCall(httpClient, url, headers, rootNode.toPrettyString());
        if (resp == null) {
            logger.warn("Null result returned sending command to LG API V1");
            throw new LGThinqApiException("Null result returned sending command to LG API V1");
        }
        return resp;
    }

    @Override
    protected Map<String, Object> handleGenericErrorResult(@Nullable RestResult resp) throws LGThinqApiException {
        Map<String, Object> metaResult;
        Map<String, Object> envelope = Collections.emptyMap();
        if (resp == null) {
            return envelope;
        }
        if (resp.getStatusCode() != 200) {
            if (resp.getStatusCode() == 400) {
                logger.warn("Error returned by LG Server API. HTTP Status: {}. The reason is: {}", resp.getStatusCode(),
                        resp.getJsonResponse());
            } else {
                logger.error("Error returned by LG Server API. HTTP Status: {}. The reason is: {}",
                        resp.getStatusCode(), resp.getJsonResponse());
                throw new LGThinqApiException(
                        String.format("Error returned by LG Server API. HTTP Status: %s. The reason is: %s",
                                resp.getStatusCode(), resp.getJsonResponse()));
            }
        } else {
            try {
                metaResult = objectMapper.readValue(resp.getJsonResponse(), new TypeReference<>() {
                });
                envelope = (Map<String, Object>) metaResult.get("lgedmRoot");
                String code = String.valueOf(envelope.get("returnCd"));
                if (envelope.isEmpty()) {
                    throw new LGThinqApiException(String.format(
                            "Unexpected json body returned (without root node lgedmRoot): %s", resp.getJsonResponse()));
                } else if (!ResultCodes.OK.containsResultCode(code)) {
                    if (ResultCodes.DEVICE_NOT_RESPONSE.containsResultCode("" + envelope.get("returnCd"))
                            || "D".equals(envelope.get("deviceState"))) {
                        logger.debug("LG API report error processing the request -> resultCode=[{}], message=[{}]",
                                code, getErrorCodeMessage(code));
                        // Disconnected Device
                        throw new LGThinqDeviceV1OfflineException("Device is offline. No data available");
                    }
                    logger.error("LG API report error processing the request -> resultCode=[{}], message=[{}]", code,
                            getErrorCodeMessage(code));
                    throw new LGThinqApiException(String
                            .format("Status error executing endpoint. resultCode must be 0000, but was:%s", code));
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unknown error occurred deserializing json stream", e);
            }
        }
        return envelope;
    }

    /**
     * Principal method to prepare the command to be sent to V1 Devices mainly when the command is generic,
     * i.e, you can send a command structure to redefine any changeable feature of the device
     * 
     * @param cmdDef command definition with template of the payload and data (binary or not)
     * @param snapData snapshot data with features to be set in the device
     * @return return the command structure.
     * @throws JsonProcessingException
     */
    protected Map<String, Object> prepareCommandV1(CommandDefinition cmdDef, Map<String, Object> snapData)
            throws JsonProcessingException {
        // expected map ordered here
        String dataStr = cmdDef.getDataTemplate();
        // Keep the order
        for (Map.Entry<String, Object> e : snapData.entrySet()) {
            String value = String.valueOf(e.getValue());
            dataStr = dataStr.replace("{{" + e.getKey() + "}}", value);
        }

        return completeCommandDataNodeV1(cmdDef, dataStr);
    }

    protected LinkedHashMap<String, Object> completeCommandDataNodeV1(CommandDefinition cmdDef, String dataStr)
            throws JsonProcessingException {
        LinkedHashMap<String, Object> data = objectMapper.readValue(cmdDef.getRawCommand(), new TypeReference<>() {
        });
        logger.debug("Prepare command v1: {}", dataStr);
        if (cmdDef.isBinary()) {
            data.put("format", "B64");
            List<Integer> list = objectMapper.readValue(dataStr, new TypeReference<>() {
            });
            // convert the list of integer to a bytearray
            byte[] bytes = ArrayUtils.toPrimitive(list.stream().map(Integer::byteValue).toArray(Byte[]::new));
            String str_data_encoded = new String(Base64.getEncoder().encode(bytes));
            data.put("data", str_data_encoded);
        } else {
            data.put("data", dataStr);
        }
        return data;
    }
}
