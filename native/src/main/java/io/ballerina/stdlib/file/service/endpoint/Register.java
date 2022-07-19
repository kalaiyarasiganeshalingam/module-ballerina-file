/*
 * Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.file.service.endpoint;

import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.stdlib.file.service.DirectoryListenerConstants;
import io.ballerina.stdlib.file.service.FSListener;
import io.ballerina.stdlib.file.utils.FileConstants;
import io.ballerina.stdlib.file.utils.FileUtils;
import org.wso2.transport.localfilesystem.server.connector.contract.LocalFileSystemConnectorFactory;
import org.wso2.transport.localfilesystem.server.connector.contract.LocalFileSystemServerConnector;
import org.wso2.transport.localfilesystem.server.connector.contractimpl.LocalFileSystemConnectorFactoryImpl;
import org.wso2.transport.localfilesystem.server.exception.LocalFileSystemServerConnectorException;
import org.wso2.transport.localfilesystem.server.util.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Register file listener service.
 */

public class Register {

    public static Object register(BObject listener, BObject service) {
        BMap serviceEndpointConfig = listener.getMapValue(DirectoryListenerConstants.SERVICE_ENDPOINT_CONFIG);
        try {
            final Map<String, MethodType> resourceRegistry = getResourceRegistry(service);
            final String events = String.join(",", resourceRegistry.keySet());
            final Map<String, String> paramMap = getParamMap(serviceEndpointConfig, events);
            LocalFileSystemConnectorFactory connectorFactory = new LocalFileSystemConnectorFactoryImpl();
            LocalFileSystemServerConnector serverConnector = connectorFactory
                    .createServerConnector(service.getType().getName(), paramMap,
                            new FSListener(Runtime.getCurrentRuntime(), service, resourceRegistry));
            listener.addNativeData(DirectoryListenerConstants.FS_SERVER_CONNECTOR, serverConnector);
        } catch (LocalFileSystemServerConnectorException e) {
            return FileUtils.getBallerinaError(FileConstants.FILE_SYSTEM_ERROR,
                    "Unable to initialize server connector: " + e.getMessage());
        }
        return null;
    }

    private static Map<String, MethodType> getResourceRegistry(BObject service) {
        Map<String, MethodType> registry = new HashMap<>(5);
        final MethodType[] attachedFunctions = service.getType().getMethods();
        for (MethodType resource : attachedFunctions) {
            switch (resource.getName()) {
                case DirectoryListenerConstants.RESOURCE_NAME_ON_CREATE:
                    registry.put(DirectoryListenerConstants.EVENT_CREATE, resource);
                    break;
                case DirectoryListenerConstants.RESOURCE_NAME_ON_DELETE:
                    registry.put(DirectoryListenerConstants.EVENT_DELETE, resource);
                    break;
                case DirectoryListenerConstants.RESOURCE_NAME_ON_MODIFY:
                    registry.put(DirectoryListenerConstants.EVENT_MODIFY, resource);
                    break;
                default:
                    // Do nothing.
            }
        }
        if (registry.size() == 0) {
            String msg = "At least a single resource required from following: "
                    + DirectoryListenerConstants.RESOURCE_NAME_ON_CREATE + " ,"
                    + DirectoryListenerConstants.RESOURCE_NAME_ON_DELETE + " ,"
                    + DirectoryListenerConstants.RESOURCE_NAME_ON_MODIFY + ". " + "Parameter should be of type - "
                    + "file:" + DirectoryListenerConstants.FILE_SYSTEM_EVENT;
            throw ErrorCreator.createError(StringUtils.fromString(msg));
        }
        return registry;
    }

    private static Map<String, String> getParamMap(BMap serviceEndpointConfig, String events) {
        final String path = serviceEndpointConfig.getStringValue(DirectoryListenerConstants.ANNOTATION_PATH).getValue();
        final boolean recursive = serviceEndpointConfig
                .getBooleanValue(DirectoryListenerConstants.ANNOTATION_DIRECTORY_RECURSIVE);
        Map<String, String> paramMap = new HashMap<>(3);
        paramMap.put(Constants.FILE_URI, path);
        paramMap.put(Constants.DIRECTORY_WATCH_EVENTS, events);
        paramMap.put(Constants.DIRECTORY_WATCH_RECURSIVE, String.valueOf(recursive));
        return paramMap;
    }

    private Register() {}
}
