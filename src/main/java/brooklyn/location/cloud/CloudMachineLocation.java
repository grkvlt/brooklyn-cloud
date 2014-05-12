/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.location.cloud;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.cloud.CloudEnvironment;
import brooklyn.entity.cloud.CloudMachine;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Maps;

public class CloudMachineLocation extends SshMachineLocation implements DynamicLocation<CloudMachine, CloudMachineLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(CloudMachineLocation.class);

    @SetFromFlag("machine")
    private SshMachineLocation machine;

    @SetFromFlag("owner")
    private CloudMachine owner;

    public CloudMachineLocation() {
        this(Maps.newLinkedHashMap());
    }

    public CloudMachineLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    @Override
    public CloudMachine getOwner() {
        return owner;
    }

    public SshMachineLocation getMachine() {
        return machine;
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .add("machine", machine)
                .add("owner", owner);
    }

    public CloudEnvironment getCloudEnvironment() {
        return ((CloudMachineLocation) getParent()).getCloudEnvironment();
    }


    public void setEntity(Entity entity) {
        owner.setRunningEntity(entity);
    }

    public Entity getEntity() {
        return owner.getRunningEntity();
    }

    @Override
    public void close() throws IOException {
        LOG.info("Close called on cloud machine location, ignoring: {}", this);
    }

}
