/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright (c) 2016 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 */
package fish.payara.appserver.micro.services.asadmin;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.util.ColumnFormatter;
import fish.payara.appserver.micro.services.PayaraInstance;
import fish.payara.appserver.micro.services.data.ApplicationDescriptor;
import fish.payara.appserver.micro.services.data.InstanceDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.inject.Inject;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

/**
 * Asadmin command to list information about members of the Domain Hazelcast Cluster
 * @author Andrew Pielage
 */
@Service(name = "list-hazelcast-cluster-members")
@PerLookup
@CommandLock(CommandLock.LockType.NONE)
@I18n("list.hazelcast.cluster.members")
@ExecuteOn(value = RuntimeType.DAS)
@TargetType(value = CommandTarget.DAS)
@RestEndpoints({
    @RestEndpoint(configBean = Domain.class,
            opType = RestEndpoint.OpType.GET,
            path = "list-hazelcast-cluster-members",
            description = "List Hazelcast Cluster Members")
})
public class ListHazelcastClusterMembersCommand implements AdminCommand
{
    @Inject
    private PayaraInstance payaraMicro;
    
    @Param(name = "type", optional = true, acceptableValues = "micro,server")
    private String type;
    
    @Override
    public void execute(AdminCommandContext context)
    {
        final ActionReport actionReport = context.getActionReport();
        
        // Check if the DAS is in a Hazelcast cluster
        if (payaraMicro.isClustered())
        {
            // Get the instance descriptors of the cluster members
            Set<InstanceDescriptor> instances = payaraMicro.getClusteredPayaras();
        
            // Create the table headers
            String[] headers = {"Instance Name", "Instance Type", "Host Name", "HTTP Ports", 
                "HTTPS Ports", "Admin Port", "Hazelcast Port", "Lite Member", "Deployed Applications"};
            ColumnFormatter columnFormatter = new ColumnFormatter(headers);

            List members = new ArrayList();
            Properties extraProps = new Properties();

            // For each instance descriptor, check if it is of the type requested and add its information to the members
            // list
            for (InstanceDescriptor instance : instances) {
                if (type != null && type.equals("micro")) {
                    if (instance.isMicroInstance()) {
                        populateMembers(members, instance, columnFormatter);
                    }       
                } else if (type != null && type.equals("server")) {
                    if (instance.isPayaraInstance()) {
                        populateMembers(members, instance, columnFormatter);
                    }  
                } else {
                    populateMembers(members, instance, columnFormatter);
                }         
            }

            // Return the instance information as both a String for console output, and in the action report for REST
            extraProps.put("members", members);
            actionReport.setExtraProperties(extraProps);
            actionReport.setMessage(columnFormatter.toString());
            actionReport.setActionExitCode(ActionReport.ExitCode.SUCCESS);
        } else {
            // If hazelcast is not enabled, just return a String stating as such
            Properties extraProps = new Properties();
            extraProps.put("members", "Hazelcast is not enabled");
            actionReport.setExtraProperties(extraProps);
            actionReport.setMessage("Hazelcast is not enabled");
        }
    }  
    
    private void populateMembers(List members, InstanceDescriptor instance, ColumnFormatter columnFormatter) {
        Object values[] = new Object[9];
        values[0] = instance.getInstanceName();
        values[1] = instance.getInstanceType();
        values[2] = instance.getHostName();
        if (instance.getHttpPorts().isEmpty()) {
            values[3] = "Disabled";
        } else {
            // Remove the bookended braces and add to the values array
            values[3] = instance.getHttpPorts().toString().substring(1, 
                    instance.getHttpPorts().toString().length() - 1);
        }

        if (instance.getHttpsPorts().isEmpty()) {
            values[4] = "Disabled";
        } else {
            // Remove the bookended braces and add to the values array
            values[4] = instance.getHttpsPorts().toString().substring(1, 
                    instance.getHttpsPorts().toString().length() - 1);
        }

        values[5] = instance.getAdminPort();
        values[6] = instance.getHazelcastPort();
        values[7] = instance.isLiteMember();

        // Find the deployed applications, remove the bookended braces, and add to the values array
        List<String> applications = new ArrayList<>();
        Collection<ApplicationDescriptor> applicationDescriptors = instance.getDeployedApplications();
        if (applicationDescriptors != null) {
            for (ApplicationDescriptor application : applicationDescriptors) {
                applications.add(application.getName());
            }
            values[8] = Arrays.toString(applications.toArray()).substring(1, 
                    Arrays.toString(applications.toArray()).length() - 1);
        } else {
            // Just return nothing if no applications found
            values[8] = "";
        }

        // Add the information to the console output table
        columnFormatter.addRow(values);

        // Add the information to the command output
        Map<String, Object> map = new HashMap<>(7);
        map.put("instanceName", values[0]);
        map.put("instanceType", values[1]);
        map.put("hostName", values[2]);
        map.put("httpPorts", values[3]);
        map.put("httpsPorts", values[4]);
        map.put("adminPort", values[5]);
        map.put("hazelcastPort", values[6]);
        map.put("liteMember", values[7]);
        map.put("applications", values[8]);

        members.add(map);
    }
}
