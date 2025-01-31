// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.datasource;

import org.apache.doris.analysis.AlterCatalogNameStmt;
import org.apache.doris.analysis.AlterCatalogPropertyStmt;
import org.apache.doris.analysis.CreateCatalogStmt;
import org.apache.doris.analysis.DropCatalogStmt;
import org.apache.doris.analysis.RefreshCatalogStmt;
import org.apache.doris.analysis.StatementBase;
import org.apache.doris.catalog.Env;
import org.apache.doris.catalog.Resource;
import org.apache.doris.common.DdlException;

import java.util.Map;
import java.util.Optional;

/**
 * A factory to create catalog instance of log or covert catalog into log.
 */
public class CatalogFactory {

    /**
     * Convert the sql statement into catalog log.
     */
    public static CatalogLog constructorCatalogLog(long catalogId, StatementBase stmt) {
        CatalogLog log = new CatalogLog();
        if (stmt instanceof CreateCatalogStmt) {
            log.setCatalogId(catalogId);
            log.setCatalogName(((CreateCatalogStmt) stmt).getCatalogName());
            log.setResource(((CreateCatalogStmt) stmt).getResource());
            log.setProps(((CreateCatalogStmt) stmt).getProperties());
        } else if (stmt instanceof DropCatalogStmt) {
            log.setCatalogId(catalogId);
        } else if (stmt instanceof AlterCatalogPropertyStmt) {
            log.setCatalogId(catalogId);
            log.setNewProps(((AlterCatalogPropertyStmt) stmt).getNewProperties());
        } else if (stmt instanceof AlterCatalogNameStmt) {
            log.setCatalogId(catalogId);
            log.setNewCatalogName(((AlterCatalogNameStmt) stmt).getNewCatalogName());
        } else if (stmt instanceof RefreshCatalogStmt) {
            log.setCatalogId(catalogId);
            log.setInvalidCache(((RefreshCatalogStmt) stmt).isInvalidCache());
        } else {
            throw new RuntimeException("Unknown stmt for catalog manager " + stmt.getClass().getSimpleName());
        }
        return log;
    }

    /**
     * create the catalog instance from catalog log.
     */
    public static CatalogIf constructorFromLog(CatalogLog log) throws DdlException {
        return constructorCatalog(log.getCatalogId(), log.getCatalogName(), log.getResource(), log.getProps());
    }

    private static CatalogIf constructorCatalog(
            long catalogId, String name, String resource, Map<String, String> props) throws DdlException {
        CatalogIf catalog;
        if (resource == null) {
            String type = props.get("type");
            if (type == null) {
                throw new DdlException("Need catalog type in properties");
            }
            switch (type) {
                case "hms":
                    catalog = new HMSExternalCatalog(catalogId, name, null, props);
                    break;
                case "es":
                    catalog = new EsExternalCatalog(catalogId, name, null, props);
                    break;
                case "jdbc":
                    catalog = new JdbcExternalCatalog(catalogId, name, null, props);
                    break;
                default:
                    throw new DdlException("Unknown catalog type: " + type);
            }
        } else if (props.size() == 0) {
            Resource catalogResource = Optional.ofNullable(Env.getCurrentEnv().getResourceMgr().getResource(resource))
                    .orElseThrow(() -> new DdlException("Resource doesn't exist: " + resource));
            Resource.ResourceType type = catalogResource.getType();
            switch (type) {
                case HMS:
                    catalog = new HMSExternalCatalog(catalogId, name, resource, props);
                    break;
                case ES:
                    catalog = new EsExternalCatalog(catalogId, name, resource, props);
                    break;
                case JDBC:
                    catalog = new JdbcExternalCatalog(catalogId, name, resource, props);
                    break;
                default:
                    throw new DdlException("Unknown catalog type with resource: " + resource + ", type: " + type);
            }
        } else {
            throw new DdlException("Can't provide resource and properties for catalog simultaneously");
        }
        return catalog;
    }
}
