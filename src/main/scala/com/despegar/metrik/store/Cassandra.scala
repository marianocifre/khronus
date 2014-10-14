/*
 * =========================================================================================
 * Copyright © 2014 the metrik project <https://github.com/hotels-tech/metrik>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package com.despegar.metrik.store

import com.despegar.metrik.util.{ Config, Logging }
import com.netflix.astyanax.AstyanaxContext
import com.netflix.astyanax.connectionpool.NodeDiscoveryType
import com.netflix.astyanax.connectionpool.impl.{ ConnectionPoolConfigurationImpl, CountingConnectionPoolMonitor }
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.thrift.ThriftFamilyFactory

import scala.collection.JavaConverters._
import scala.util.Try

object Cassandra extends Config with Logging {

  val cassandraConfig = config.getConfig("metrik.cassandra")

  private val context = new AstyanaxContext.Builder().forCluster(cassandraConfig.getString("cluster"))
    .forKeyspace(cassandraConfig.getString("keyspace"))
    .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
      .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE))
    .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("CassandraConnectionPool")
      .setPort(cassandraConfig.getInt("port"))
      .setMaxConnsPerHost(1)
      .setSeeds(cassandraConfig.getString("seeds")))
    .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
    .buildKeyspace(ThriftFamilyFactory.getInstance())

  context.start()

  val keyspace = context.getClient()

  def initialize = {
    initializeKeyspace
    CassandraMetaStore.initialize
    CassandraHistogramBucketStore.initialize
    CassandraStatisticSummaryStore.initialize
  }

  private def initializeKeyspace = {
    Try {
      log.info("Initializing metrik keyspace...")
      keyspace.createKeyspaceIfNotExists(
        Map("strategy_options" -> Map("replication_factor" -> "1").asJava, "strategy_class" -> "SimpleStrategy").asJava)
        .getResult();
    }
  }

  def createColumnFamily[T, U](columnFamily: ColumnFamily[T, U]) = Try {
    log.info(s"Initializing columnFamily[${columnFamily.getName()}]...")
    keyspace.createColumnFamily(columnFamily, Map[String, Object]().asJava)
    log.info(s"columnFamily[${columnFamily.getName()}] created successfully")
  }

}