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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lgthinq.internal.discovery.LGThinqDiscoveryService;
import org.openhab.binding.lgthinq.lgservices.model.CapabilityDefinition;
import org.openhab.binding.lgthinq.lgservices.model.SnapshotDefinition;

/**
 * The {@link LGThinQBridge}
 *
 * @author Nemer Daud - Initial contribution
 */
@NonNullByDefault
public interface LGThinQBridge {
    /**
     * Register
     * 
     * @param listener
     */
    void registerDiscoveryListener(LGThinqDiscoveryService listener);

    void registryListenerThing(
            LGThinQAbstractDeviceHandler<? extends CapabilityDefinition, ? extends SnapshotDefinition> thing);

    void unRegistryListenerThing(
            LGThinQAbstractDeviceHandler<? extends CapabilityDefinition, ? extends SnapshotDefinition> thing);
}
