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

import static org.apache.hadoop.yarn.util.StringHelper.join;
import static org.apache.hadoop.yarn.webapp.view.JQueryUI.C_PROGRESSBAR;
import static org.apache.hadoop.yarn.webapp.view.JQueryUI.C_PROGRESSBAR_VALUE;

import java.util.Set;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.yarn.api.ApplicationBaseProtocol;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.server.resourcemanager.ResourceManager;
import org.apache.hadoop.yarn.server.webapp.AppsBlock;
import org.apache.hadoop.yarn.server.webapp.dao.AppInfo;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.webapp.View;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.TABLE;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.TBODY;

import com.google.inject.Inject;

public class RMAppsBlock extends AppsBlock {

  private ResourceManager rm;

  @Inject
  RMAppsBlock(ResourceManager rm, ApplicationBaseProtocol appBaseProt,
      View.ViewContext ctx) {
    super(appBaseProt, ctx);
    this.rm = rm;
  }

  @Override
  protected void renderData(Block html) {
    TBODY<TABLE<Hamlet>> tbody =
        html.table("#apps").thead().tr().th(".id", "ID").th(".user", "用户")
          .th(".name", "名称").th(".type", "应用类型")
          .th(".queue", "队列").th(".priority", "优先级")
          .th(".starttime", "开始时间")
          .th(".finishtime", "结束时间").th(".state", "状态")
          .th(".finalstatus", "结果")
          .th(".runningcontainer", "运行Containers")
          .th(".allocatedCpu", "分配核数")
          .th(".allocatedMemory", "分配内存")
          .th(".queuePercentage", "队列比例")
          .th(".clusterPercentage", "集群比例")
          .th(".progress", "进度")
          .th(".ui", "运行图")
          .th(".blacklisted", "失效节点")._()
          ._().tbody();

    StringBuilder appsTableData = new StringBuilder("[\n");
    for (ApplicationReport appReport : appReports) {
      // TODO: remove the following condition. It is still here because
      // the history side implementation of ApplicationBaseProtocol
      // hasn't filtering capability (YARN-1819).
      if (!reqAppStates.isEmpty()
          && !reqAppStates.contains(appReport.getYarnApplicationState())) {
        continue;
      }

      AppInfo app = new AppInfo(appReport);
      ApplicationAttemptId appAttemptId =
          ConverterUtils.toApplicationAttemptId(app.getCurrentAppAttemptId());
      String queuePercent = "N/A";
      String clusterPercent = "N/A";
      if(appReport.getApplicationResourceUsageReport() != null) {
        queuePercent = String.format("%.1f",
            appReport.getApplicationResourceUsageReport()
                .getQueueUsagePercentage());
        clusterPercent = String.format("%.1f",
            appReport.getApplicationResourceUsageReport().getClusterUsagePercentage());
      }

      String blacklistedNodesCount = "N/A";
      Set<String> nodes =
          RMAppAttemptBlock.getBlacklistedNodes(rm, appAttemptId);
      if (nodes != null) {
        blacklistedNodesCount = String.valueOf(nodes.size());
      }
      String percent = String.format("%.1f", app.getProgress());
      // AppID numerical value parsed by parseHadoopID in yarn.dt.plugins.js
      appsTableData
        .append("[\"<a href='")
        .append(url("app", app.getAppId()))
        .append("'>")
        .append(app.getAppId())
        .append("</a>\",\"")
        .append(
          StringEscapeUtils.escapeJavaScript(
              StringEscapeUtils.escapeHtml(app.getUser())))
        .append("\",\"")
        .append(
          StringEscapeUtils.escapeJavaScript(
              StringEscapeUtils.escapeHtml(app.getName())))
        .append("\",\"")
        .append(
          StringEscapeUtils.escapeJavaScript(StringEscapeUtils.escapeHtml(app
            .getType())))
        .append("\",\"")
        .append(
          StringEscapeUtils.escapeJavaScript(StringEscapeUtils.escapeHtml(app
             .getQueue()))).append("\",\"").append(String
             .valueOf(app.getPriority()))
        .append("\",\"").append(app.getStartedTime())
        .append("\",\"").append(app.getFinishedTime())
        .append("\",\"")
        .append(app.getAppState() == null ? UNAVAILABLE : app.getAppState())
        .append("\",\"")
        .append(app.getFinalAppStatus())
        .append("\",\"")
        .append(app.getRunningContainers() == -1 ? "N/A" : String
            .valueOf(app.getRunningContainers()))
        .append("\",\"")
        .append(app.getAllocatedCpuVcores() == -1 ? "N/A" : String
            .valueOf(app.getAllocatedCpuVcores()))
        .append("\",\"")
        .append(app.getAllocatedMemoryMB() == -1 ? "N/A" :
            String.valueOf(app.getAllocatedMemoryMB()))
        .append("\",\"")
        .append(queuePercent)
        .append("\",\"")
        .append(clusterPercent)
        .append("\",\"")
        // Progress bar
          .append("<br title='").append(percent).append("'> <div class='")
        .append(C_PROGRESSBAR).append("' title='").append(join(percent, '%'))
        .append("'> ").append("<div class='").append(C_PROGRESSBAR_VALUE)
        .append("' style='").append(join("width:", percent, '%'))
        .append("'> </div> </div>").append("\",\"<a ");

      String trackingURL =
          app.getTrackingUrl() == null
              || app.getTrackingUrl().equals(UNAVAILABLE) ? null : app
            .getTrackingUrl();

      String trackingUI =
          app.getTrackingUrl() == null
              || app.getTrackingUrl().equals(UNAVAILABLE) ? "Unassigned" : app
            .getAppState() == YarnApplicationState.FINISHED
              || app.getAppState() == YarnApplicationState.FAILED
              || app.getAppState() == YarnApplicationState.KILLED ? "History"
              : "ApplicationMaster";
      appsTableData.append(trackingURL == null ? "#" : "href='" + trackingURL)
        .append("'>").append(trackingUI).append("</a>\",").append("\"")
        .append(blacklistedNodesCount).append("\"],\n");

    }
    if (appsTableData.charAt(appsTableData.length() - 2) == ',') {
      appsTableData.delete(appsTableData.length() - 2,
        appsTableData.length() - 1);
    }
    appsTableData.append("]");
    html.script().$type("text/javascript")
      ._("var appsTableData=" + appsTableData)._();

    tbody._()._();
  }
}
