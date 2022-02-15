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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.model.DevicePowerState;
import org.openhab.binding.lgthinq.lgservices.model.dryer.DryerCapability;
import org.openhab.binding.lgthinq.lgservices.model.dryer.DryerSnapshot;

/**
 * The {@link LGThinQDRApiV2ClientServiceImpl}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public class LGThinQDRApiV2ClientServiceImpl extends LGThinQAbstractApiClientService<DryerCapability, DryerSnapshot>
        implements LGThinQDRApiClientService {

    private static final LGThinQDRApiV2ClientServiceImpl instance;
    static {
        instance = new LGThinQDRApiV2ClientServiceImpl(DryerCapability.class, DryerSnapshot.class);
    }

    protected LGThinQDRApiV2ClientServiceImpl(Class<DryerCapability> capabilityClass,
            Class<DryerSnapshot> snapshotClass) {
        super(capabilityClass, snapshotClass);
    }

    public static LGThinQDRApiV2ClientServiceImpl getInstance() {
        return instance;
    }

    @Override
    public void turnDevicePower(String bridgeName, String deviceId, DevicePowerState newPowerState)
            throws LGThinqApiException {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
