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
package brooklyn.entity.cloud;

import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.SshMachineLocation;

public interface MachineEntity extends Entity, Startable {

    AttributeSensor<Boolean> SSH_AVAILABLE = Sensors.newBooleanSensor("machine.sshable", "Is the machine accessible over SSH");

    AttributeSensor<Double> CPU_USAGE = Sensors.newDoubleSensor("machine.cpuUsage", "The machine CPU usage");

    AttributeSensor<SshMachineLocation> SSH_MACHINE = Sensors.newSensor(SshMachineLocation.class, "machine.sshMachineLocation", "The SSHable machine");

    AttributeSensor<String> OPERATING_SYSTEM = Sensors.newStringSensor("machine.os", "The machine operating system");

    SshMachineLocation getSshMachine();

    boolean isSshable();

}
