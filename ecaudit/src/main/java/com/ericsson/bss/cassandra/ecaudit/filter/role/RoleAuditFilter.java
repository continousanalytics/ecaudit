/*
 * Copyright 2018 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecaudit.filter.role;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.UncheckedExecutionException;

import com.ericsson.bss.cassandra.ecaudit.auth.AuditWhitelistCache;
import com.ericsson.bss.cassandra.ecaudit.auth.Exceptions;
import com.ericsson.bss.cassandra.ecaudit.auth.WhitelistDataAccess;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.filter.AuditFilter;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;
import org.apache.cassandra.auth.Roles;

/**
 * A role based white-list filter that exempts users based on custom options on roles in Cassandra.
 */
public class RoleAuditFilter implements AuditFilter
{
    private static final int MAX_RESOURCE_DEPTH = 3;

    private final Function<RoleResource, Set<RoleResource>> getRolesFunction;
    private final AuditWhitelistCache whitelistCache;
    private final WhitelistDataAccess whitelistDataAccess;

    public RoleAuditFilter()
    {
        this(Roles::getRoles, AuditWhitelistCache.getInstance(), WhitelistDataAccess.getInstance());
    }

    @VisibleForTesting
    RoleAuditFilter(Function<RoleResource, Set<RoleResource>> getRolesFunction, AuditWhitelistCache whitelistCache, WhitelistDataAccess whitelistDataAccess)
    {
        this.getRolesFunction = getRolesFunction;
        this.whitelistCache = whitelistCache;
        this.whitelistDataAccess = whitelistDataAccess;
    }

    @Override
    public void setup()
    {
        whitelistDataAccess.setup();
    }

    /**
     * Returns true if the supplied log entry's role or any other role granted to it (directly or indirectly) is
     * white-listed for the log entry's specified operations and resource.
     *
     * If several operations are specified by the log entry (which typically happens on CAS operations), then all of the
     * operations must be whitelisted.
     *
     * A resource is considered to be whitelisted if it, or any of its parents are mentioned in a roles whitelist.
     *
     * @param logEntry
     *            the log entry specifying the primary role as well as operation and resource
     * @return true if the operation is white-listed, false otherwise
     */
    @Override
    public boolean isFiltered(AuditEntry logEntry)
    {
        try
        {
            return isFilteredUnchecked(logEntry);
        }
        catch (UncheckedExecutionException e)
        {
            throw Exceptions.tryGetCassandraExceptionCause(e);
        }
    }

    private boolean isFilteredUnchecked(AuditEntry logEntry)
    {
        Set<RoleResource> roles = getRoles(logEntry.getUser());
        List<IResource> operationResourceHierarchy = getResourceHierarchy(logEntry.getResource());
        for (Permission operation : logEntry.getPermissions())
        {
            if(!isOperationWhitelistedOnResourceByRoles(operation, operationResourceHierarchy, roles))
            {
                return false;
            }
        }

        return true;
    }

    private Set<RoleResource> getRoles(String username)
    {
        RoleResource primaryRole = RoleResource.role(username);
        return getRolesFunction.apply(primaryRole);
    }

    private List<IResource> getResourceHierarchy(IResource primaryResource)
    {
        List<IResource> resourceList = new ArrayList<>(MAX_RESOURCE_DEPTH);
        resourceList.add(primaryResource);
        IResource resource = primaryResource;
        while (resource.hasParent())
        {
            resource = resource.getParent();
            resourceList.add(resource);
        }
        return resourceList;
    }

    private boolean isOperationWhitelistedOnResourceByRoles(Permission operation, List<IResource> operationResourceHierarchy, Set<RoleResource> roles)
    {
        for (RoleResource role : roles)
        {
            if (isOperationWhitelistedOnResourceByRole(operation, operationResourceHierarchy, role))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isOperationWhitelistedOnResourceByRole(Permission operation, List<IResource> operationResourceHierarchy, RoleResource role)
    {
        Map<IResource, Set<Permission>> whitelist = whitelistCache.getWhitelist(role);
        for (IResource resource : operationResourceHierarchy)
        {
            Set<Permission> whitelistedOperations = whitelist.get(resource);
            if (whitelistContains(operation, whitelistedOperations))
            {
                return true;
            }
        }

        return false;
    }

    private boolean whitelistContains(Permission operation, Set<Permission> whitelistedOperations)
    {
        return whitelistedOperations != null && whitelistedOperations.contains(operation);
    }
}
