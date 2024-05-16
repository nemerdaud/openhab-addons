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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.model.devices.washerdryer.WasherDryerCapability;
import org.openhab.binding.lgthinq.lgservices.model.devices.washerdryer.WasherDryerSnapshot;

/**
 * The {@link LGThinQWMApiClientService}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public interface LGThinQWMApiClientService extends LGThinQApiClientService<WasherDryerCapability, WasherDryerSnapshot> {
    void remoteStart(String bridgeName, WasherDryerCapability cap, String deviceId, Map<String, Object> data)
            throws LGThinqApiException;

    void wakeUp(String bridgeName, String deviceId, Boolean wakeUp) throws LGThinqApiException;
}
