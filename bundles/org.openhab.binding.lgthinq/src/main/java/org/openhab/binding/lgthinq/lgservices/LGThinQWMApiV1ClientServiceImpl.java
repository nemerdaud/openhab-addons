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

import java.io.IOException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.model.DevicePowerState;
import org.openhab.binding.lgthinq.lgservices.model.washer.WasherCapability;
import org.openhab.binding.lgthinq.lgservices.model.washer.WasherSnapshot;

/**
 * The {@link LGThinQWMApiV1ClientServiceImpl}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinQWMApiV1ClientServiceImpl extends LGThinQAbstractApiClientService<WasherCapability, WasherSnapshot>
        implements LGThinQWMApiClientService {

    private static final LGThinQWMApiClientService instance;
    static {
        instance = new LGThinQWMApiV1ClientServiceImpl(WasherCapability.class, WasherSnapshot.class);
    }

    protected LGThinQWMApiV1ClientServiceImpl(Class<WasherCapability> capabilityClass,
            Class<WasherSnapshot> snapshotClass) {
        super(capabilityClass, snapshotClass);
    }

    @Override
    protected void beforeGetDataDevice(@NonNull String bridgeName, @NonNull String deviceId) {
        // Nothing to do for V1 thinq
    }

    @Override
    public double getInstantPowerConsumption(@NonNull String bridgeName, @NonNull String deviceId)
            throws LGThinqApiException, IOException {
        return 0;
    }

    public static LGThinQWMApiClientService getInstance() {
        return instance;
    }

    @Override
    public void turnDevicePower(String bridgeName, String deviceId, DevicePowerState newPowerState)
            throws LGThinqApiException {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    @Nullable
    public WasherSnapshot getDeviceData(@NonNull String bridgeName, @NonNull String deviceId)
            throws LGThinqApiException {
        throw new UnsupportedOperationException("Method not supported in V1 API device.");
    }
}
