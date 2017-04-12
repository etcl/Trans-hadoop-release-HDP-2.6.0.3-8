/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.hadoop.yarn.server.resourcemanager.webapp;

import org.apache.hadoop.yarn.server.resourcemanager.ResourceManager;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.ClusterInfo;
import org.apache.hadoop.yarn.util.Times;
import org.apache.hadoop.yarn.webapp.view.HtmlBlock;
import org.apache.hadoop.yarn.webapp.view.InfoBlock;

import com.google.inject.Inject;

public class AboutBlock extends HtmlBlock {
  final ResourceManager rm;

  @Inject
  AboutBlock(ResourceManager rm, ViewContext ctx) {
    super(ctx);
    this.rm = rm;
  }

  @Override
  protected void render(Block html) {
    html._(MetricsOverviewTable.class);
    ResourceManager rm = getInstance(ResourceManager.class);
    ClusterInfo cinfo = new ClusterInfo(rm);
    info("集群总览").
      _("集群 ID:", cinfo.getClusterId()).
      _("资源管理器状态:", cinfo.getState()).
      _("资源管理器HA 状态:", cinfo.getHAState()).
      _("资源管理器HA zookeeper连接状态:",
          cinfo.getHAZookeeperConnectionState()).
          _("资源管理器 RMStateStore:", cinfo.getRMStateStore()).
          _("资源管理器启动时间:", Times.format(cinfo.getStartedOn())).
          _("资源管理器版本:", cinfo.getRMBuildVersion() +
          " on " + cinfo.getRMVersionBuiltOn()).
      _("Hadoop版本:", cinfo.getHadoopBuildVersion() +
          " on " + cinfo.getHadoopVersionBuiltOn());
    html._(InfoBlock.class);
  }

}
