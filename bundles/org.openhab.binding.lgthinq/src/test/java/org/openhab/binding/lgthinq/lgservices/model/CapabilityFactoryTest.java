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
package org.openhab.binding.lgthinq.lgservices.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openhab.binding.lgthinq.handler.JsonUtils;
import org.openhab.binding.lgthinq.internal.errors.LGThinqApiException;
import org.openhab.binding.lgthinq.lgservices.model.washerdryer.WasherCapability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link CapabilityFactoryTest}
 *
 * @author Nemer Daud - Initial contribution
 */
class CapabilityFactoryTest {
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void create() throws IOException, LGThinqApiException {
        ClassLoader classLoader = JsonUtils.class.getClassLoader();
        assertNotNull(classLoader);
        URL fileUrl = classLoader.getResource("thinq-washer-v2-cap.json");
        assertNotNull(fileUrl);
        File capFile = new File(fileUrl.getFile());
        Map<String, Object> mapper = objectMapper.readValue(capFile, new TypeReference<>() {
        });
        WasherCapability wpCap = (WasherCapability) CapabilityFactory.getInstance().create(mapper,
                WasherCapability.class);
        assertNotNull(wpCap);
        assertEquals(14, wpCap.getCourses().size());
        assertTrue(wpCap.getRinse().size() > 1);
        assertTrue(wpCap.getSpin().size() > 1);
        assertTrue(wpCap.getSoilWash().size() > 1);
        assertTrue(wpCap.getTemperature().size() > 1);
        assertTrue(wpCap.hasDoorLook());
        assertTrue(wpCap.hasTurboWash());
    }
}
