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
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

public interface CloudAttributes {

    AttributeSensor<Entity> ENTITY = Sensors.newSensor(Entity.class, "cloud.machine.entity", "The entity running in this machine");

    AttributeSensor<Double> CPU_USAGE = Sensors.newDoubleSensor("cloud.cpuUsage", "Current CPU usage");
    AttributeSensor<Double> AVERAGE_CPU_USAGE = Sensors.newDoubleSensor("cloud.cpuUsage.average", "Average CPU usage");

    /*
     * Counter attributes.
     */

    AttributeSensor<Integer> CLOUD_MACHINE_COUNT = Sensors.newIntegerSensor("cloud.machine.count", "Number of cloud machines");
    AttributeSensor<Integer> CLOUD_MACHINE_IDLE_COUNT = Sensors.newIntegerSensor("cloud.machine.idleCount", "Number of idle cloud machines");
}
