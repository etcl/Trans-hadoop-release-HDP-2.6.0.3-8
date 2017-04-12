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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.api.records.NodeLabel;
import org.apache.hadoop.yarn.nodelabels.RMNodeLabel;
import org.apache.hadoop.yarn.server.resourcemanager.ResourceManager;
import org.apache.hadoop.yarn.server.resourcemanager.nodelabels.RMNodeLabelsManager;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerHealth;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CSQueue;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacityScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.UserInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.CapacitySchedulerInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.CapacitySchedulerLeafQueueInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.CapacitySchedulerQueueInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.PartitionQueueCapacitiesInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.PartitionResourcesInfo;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.ResourceInfo;
import org.apache.hadoop.yarn.server.security.ApplicationACLsManager;
import org.apache.hadoop.yarn.server.webapp.AppBlock;
import org.apache.hadoop.yarn.util.Times;
import org.apache.hadoop.yarn.util.resource.Resources;
import org.apache.hadoop.yarn.webapp.ResponseInfo;
import org.apache.hadoop.yarn.webapp.SubView;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.DIV;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.LI;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.TABLE;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.TBODY;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.UL;
import org.apache.hadoop.yarn.webapp.view.HtmlBlock;
import org.apache.hadoop.yarn.webapp.view.InfoBlock;

import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;

class CapacitySchedulerPage extends RmView {
  static final String _Q = ".ui-state-default.ui-corner-all";
  static final float Q_MAX_WIDTH = 0.8f;
  static final float Q_STATS_POS = Q_MAX_WIDTH + 0.05f;
  static final String Q_END = "left:101%";
  static final String Q_GIVEN = "left:0%;background:none;border:1px dashed rgba(0,0,0,0.25)";
  static final String Q_OVER = "background:rgba(255, 140, 0, 0.8)";
  static final String Q_UNDER = "background:rgba(50, 205, 50, 0.8)";

  @RequestScoped
  static class CSQInfo {
    CapacitySchedulerInfo csinfo;
    CapacitySchedulerQueueInfo qinfo;
    String label;
    boolean isExclusiveNodeLabel;
  }

  static class LeafQueueInfoBlock extends HtmlBlock {
    final CapacitySchedulerLeafQueueInfo lqinfo;
    private String nodeLabel;

    @Inject LeafQueueInfoBlock(ViewContext ctx, CSQInfo info) {
      super(ctx);
      lqinfo = (CapacitySchedulerLeafQueueInfo) info.qinfo;
      nodeLabel = info.label;
    }

    @Override
    protected void render(Block html) {
      if (nodeLabel == null) {
        renderLeafQueueInfoWithoutParition(html);
      } else {
        renderLeafQueueInfoWithPartition(html);
      }
    }

    private void renderLeafQueueInfoWithPartition(Block html) {
      String nodeLabelDisplay = nodeLabel.length() == 0
          ? NodeLabel.DEFAULT_NODE_LABEL_PARTITION : nodeLabel;
      // first display the queue's label specific details :
      ResponseInfo ri =
          info("\'" + lqinfo.getQueuePath().substring(5)
              + "\' Partition队列状态 \'" + nodeLabelDisplay + "\'");
      renderQueueCapacityInfo(ri, nodeLabel);
      html._(InfoBlock.class);
      // clear the info contents so this queue's info doesn't accumulate into
      // another queue's info
      ri.clear();

      // second display the queue specific details :
      ri =
          info("\'" + lqinfo.getQueuePath().substring(5) + "\' 队列状态")
              ._("Queue State:", lqinfo.getQueueState());
      renderCommonLeafQueueInfo(ri);

      html._(InfoBlock.class);
      // clear the info contents so this queue's info doesn't accumulate into
      // another queue's info
      ri.clear();
    }

    private void renderLeafQueueInfoWithoutParition(Block html) {
      ResponseInfo ri =
          info("\'" + lqinfo.getQueuePath().substring(5) + "\' 队列状态")
              ._("队列状态:", lqinfo.getQueueState());
      renderQueueCapacityInfo(ri, "");
      renderCommonLeafQueueInfo(ri);
      html._(InfoBlock.class);
      // clear the info contents so this queue's info doesn't accumulate into
      // another queue's info
      ri.clear();
    }

    private void renderQueueCapacityInfo(ResponseInfo ri, String label) {
      PartitionQueueCapacitiesInfo capacities =
          lqinfo.getCapacities().getPartitionQueueCapacitiesInfo(label);
      PartitionResourcesInfo resourceUsages =
          lqinfo.getResources().getPartitionResourceUsageInfo(label);

      // Get UserInfo from first user to calculate AM Resource Limit per user.
      ResourceInfo userAMResourceLimit = null;
      ArrayList<UserInfo> usersList = lqinfo.getUsers().getUsersList();
      if (usersList.isEmpty()) {
        // If no users are present, consider AM Limit for that queue.
        userAMResourceLimit = resourceUsages.getAMLimit();
      } else {
        userAMResourceLimit = usersList.get(0)
            .getResourceUsageInfo().getPartitionResourceUsageInfo(label)
            .getAMLimit();
      }
      ResourceInfo amUsed = (resourceUsages.getAmUsed() == null)
          ? new ResourceInfo(Resources.none())
          : resourceUsages.getAmUsed();
      ri.
      _("已使用容量:", percent(capacities.getUsedCapacity() / 100)).
      _("配置容量:", percent(capacities.getCapacity() / 100)).
      _("配置最大容量:", percent(capacities.getMaxCapacity() / 100)).
      _("完全使用容量:", percent(capacities.getAbsoluteUsedCapacity() / 100)).
      _("完全配置容量:", percent(capacities.getAbsoluteCapacity() / 100)).
      _("完全配置最大容量:", percent(capacities.getAbsoluteMaxCapacity() / 100)).
      _("已使用资源:", resourceUsages.getUsed().toString()).
      _("配置最大 Master 限制:", StringUtils.format("%.1f",
          capacities.getMaxAMLimitPercentage())).
      _("最大Master资源:",
          resourceUsages.getAMLimit().toString()).
      _("已使用Master资源:",
          amUsed.toString()).
      _("单用户最大Master资源:",
          userAMResourceLimit.toString());
    }

    private void renderCommonLeafQueueInfo(ResponseInfo ri) {
      ri.
      _("可调度应用:", Integer.toString(lqinfo.getNumActiveApplications())).
      _("非可调度应用:", Integer.toString(lqinfo.getNumPendingApplications())).
      _("Containers数量:", Integer.toString(lqinfo.getNumContainers())).
      _("最大 Applications:", Integer.toString(lqinfo.getMaxApplications())).
      _("单用户最大 Applications:", Integer.toString(lqinfo.getMaxApplicationsPerUser())).
      _("配置最小用户限制:", Integer.toString(lqinfo.getUserLimit()) + "%").
      _("配置用户限制 Factor:", StringUtils.format(
          "%.3f", lqinfo.getUserLimitFactor()).replaceAll("\\.?0*$", "")).
      _("节点标签:", StringUtils.join(",", lqinfo.getNodeLabels())).
      _("排序策略: ", lqinfo.getOrderingPolicyInfo()).
      _("优先设置:", lqinfo.getPreemptionDisabled() ? "disabled" : "enabled").
      _("默认节点标签:",
              lqinfo.getDefaultNodeLabelExpression() == null
                  ? NodeLabel.DEFAULT_NODE_LABEL_PARTITION
                  : lqinfo.getDefaultNodeLabelExpression());
    }
  }

  static class QueueUsersInfoBlock extends HtmlBlock {
    final CapacitySchedulerLeafQueueInfo lqinfo;
    private String nodeLabel;

    @Inject
    QueueUsersInfoBlock(ViewContext ctx, CSQInfo info) {
      super(ctx);
      lqinfo = (CapacitySchedulerLeafQueueInfo) info.qinfo;
      nodeLabel = info.label;
    }

    @Override
    protected void render(Block html) {
      TBODY<TABLE<Hamlet>> tbody =
          html.table("#userinfo").thead().$class("ui-widget-header").tr().th()
              .$class("ui-state-default")._("用户名")._().th()
              .$class("ui-state-default")._("最大资源")._().th()
              .$class("ui-state-default")._("已使用资源")._().th()
              .$class("ui-state-default")._("最大 AM 资源")._().th()
              .$class("ui-state-default")._("已使用 AM 资源")._().th()
              .$class("ui-state-default")._("可调度应用")._().th()
              .$class("ui-state-default")._("非可调度应用")._()._()._()
              .tbody();

      ArrayList<UserInfo> users = lqinfo.getUsers().getUsersList();
      for (UserInfo userInfo : users) {
        ResourceInfo resourcesUsed = userInfo.getResourcesUsed();
        PartitionResourcesInfo resourceUsages = lqinfo
            .getResources()
            .getPartitionResourceUsageInfo((nodeLabel == null) ? "" : nodeLabel);
        if (nodeLabel != null) {
          resourcesUsed = userInfo.getResourceUsageInfo()
              .getPartitionResourceUsageInfo(nodeLabel).getUsed();
        }
        ResourceInfo amUsed = (resourceUsages.getAmUsed() == null)
            ? new ResourceInfo(Resources.none())
            : resourceUsages.getAmUsed();
        tbody.tr().td(userInfo.getUsername())
            .td(userInfo.getUserResourceLimit().toString())
            .td(resourcesUsed.toString())
            .td(resourceUsages.getAMLimit().toString())
            .td(amUsed.toString())
            .td(Integer.toString(userInfo.getNumActiveApplications()))
            .td(Integer.toString(userInfo.getNumPendingApplications()))._();
      }

      html.div().$class("usersinfo").h5("活跃用户信息")._();
      tbody._()._();
    }
  }

  public static class QueueBlock extends HtmlBlock {
    final CSQInfo csqinfo;

    @Inject QueueBlock(CSQInfo info) {
      csqinfo = info;
    }

    @Override
    public void render(Block html) {
      ArrayList<CapacitySchedulerQueueInfo> subQueues = (csqinfo.qinfo == null)
          ? csqinfo.csinfo.getQueues().getQueueInfoList()
          : csqinfo.qinfo.getQueues().getQueueInfoList();

      UL<Hamlet> ul = html.ul("#pq");
      float used;
      float absCap;
      float absMaxCap;
      float absUsedCap;
      for (CapacitySchedulerQueueInfo info : subQueues) {
        String nodeLabel = (csqinfo.label == null) ? "" : csqinfo.label;
        //DEFAULT_NODE_LABEL_PARTITION is accessible to all queues
        //other exclsiveNodeLabels are accessible only if configured
        if (!nodeLabel.isEmpty()// i.e. its DEFAULT_NODE_LABEL_PARTITION
            && csqinfo.isExclusiveNodeLabel
            && !info.getNodeLabels().contains("*")
            && !info.getNodeLabels().contains(nodeLabel)) {
          continue;
        }
        PartitionQueueCapacitiesInfo partitionQueueCapsInfo = info
            .getCapacities().getPartitionQueueCapacitiesInfo(nodeLabel);
        used = partitionQueueCapsInfo.getUsedCapacity() / 100;
        absCap = partitionQueueCapsInfo.getAbsoluteCapacity() / 100;
        absMaxCap = partitionQueueCapsInfo.getAbsoluteMaxCapacity() / 100;
        absUsedCap = partitionQueueCapsInfo.getAbsoluteUsedCapacity() / 100;

        LI<UL<Hamlet>> li = ul.
          li().
            a(_Q).$style(width(absMaxCap * Q_MAX_WIDTH)).
              $title(join("Absolute Capacity:", percent(absCap))).
              span().$style(join(Q_GIVEN, ";font-size:1px;", width(absCap/absMaxCap))).
                _('.')._().
              span().$style(join(width(absUsedCap/absMaxCap),
                ";font-size:1px;left:0%;", absUsedCap > absCap ? Q_OVER : Q_UNDER)).
                _('.')._().
              span(".q", "Queue: "+info.getQueuePath().substring(5))._().
            span().$class("qstats").$style(left(Q_STATS_POS)).
              _(join(percent(used), " used"))._();

        csqinfo.qinfo = info;
        if (info.getQueues() == null) {
          li.ul("#lq").li()._(LeafQueueInfoBlock.class)._()._();
          li.ul("#lq").li()._(QueueUsersInfoBlock.class)._()._();
        } else {
          li._(QueueBlock.class);
        }
        li._();
      }

      ul._();
    }
  }

  static class QueuesBlock extends HtmlBlock {
    final CapacityScheduler cs;
    final CSQInfo csqinfo;
    private final ResourceManager rm;
    private List<RMNodeLabel> nodeLabelsInfo;

    @Inject QueuesBlock(ResourceManager rm, CSQInfo info) {
      cs = (CapacityScheduler) rm.getResourceScheduler();
      csqinfo = info;
      this.rm = rm;
      RMNodeLabelsManager nodeLabelManager =
          rm.getRMContext().getNodeLabelManager();
      nodeLabelsInfo = nodeLabelManager.pullRMNodeLabelsInfo();
    }

    @Override
    public void render(Block html) {
      html._(MetricsOverviewTable.class);

      UserGroupInformation callerUGI = this.getCallerUGI();
      boolean isAdmin = false;
      ApplicationACLsManager aclsManager = rm.getApplicationACLsManager();
      if (aclsManager.areACLsEnabled()) {
        if (callerUGI != null && aclsManager.isAdmin(callerUGI)) {
          isAdmin = true;
        }
      } else {
        isAdmin = true;
      }

      // only show button to dump CapacityScheduler debug logs to admins
      if (isAdmin) {
        html.div()
          .button()
          .$style(
              "border-style: solid; border-color: #000000; border-width: 1px;"
                  + " cursor: hand; cursor: pointer; border-radius: 4px")
              .$onclick("confirmAction()").b("生成调度器日志")._().select()
              .$id("time").option().$value("60")._("1 分钟")._().option()
              .$value("300")._("5 分钟")._().option().$value("600")._("10 分钟")._()
          ._()._();

        StringBuilder script = new StringBuilder();
        script
          .append("function confirmAction() {")
          .append(" b = confirm(\"Are you sure you wish to generate"
              + " scheduler logs?\");")
          .append(" if (b == true) {")
          .append(" var timePeriod = $(\"#time\").val();")
          .append(" $.ajax({")
          .append(" type: 'POST',")
          .append(" url: '/ws/v1/cluster/scheduler/logs',")
          .append(" contentType: 'text/plain',")
          .append(AppBlock.getCSRFHeaderString(rm.getConfig()))
          .append(" data: 'time=' + timePeriod,")
          .append(" dataType: 'text'")
          .append(" }).done(function(data){")
          .append(" setTimeout(function(){")
          .append(" alert(\"Scheduler log is being generated.\");")
          .append(" }, 1000);")
          .append(" }).fail(function(data){")
          .append(
              " alert(\"Scheduler log generation failed. Please check the"
                  + " ResourceManager log for more informtion.\");")
          .append(" console.log(data);").append(" });").append(" }")
          .append("}");

        html.script().$type("text/javascript")._(script.toString())._();
      }

      UL<DIV<DIV<Hamlet>>> ul = html.
        div("#cs-wrapper.ui-widget").
          div(".ui-widget-header.ui-corner-top").
            _("队列信息")._().
          div("#cs.ui-widget-content.ui-corner-bottom").
            ul();
      if (cs == null) {
        ul.
          li().
            a(_Q).$style(width(Q_MAX_WIDTH)).
              span().$style(Q_END)._("100% ")._().
              span(".q", "default")._()._();
      } else {
        ul.
          li().$style("margin-bottom: 1em").
            span().$style("font-weight: bold")._("图例:")._().
            span().$class("qlegend ui-corner-all").$style(Q_GIVEN).
              _("容量")._().
            span().$class("qlegend ui-corner-all").$style(Q_UNDER).
              _("已使用")._().
            span().$class("qlegend ui-corner-all").$style(Q_OVER).
              _("已使用（超过容量）")._().
            span().$class("qlegend ui-corner-all ui-state-default").
              _("最大容量")._().
          _();

        float used = 0;

        CSQueue root = cs.getRootQueue();
        CapacitySchedulerInfo sinfo = new CapacitySchedulerInfo(root, cs);
        csqinfo.csinfo = sinfo;

        boolean hasAnyLabelLinkedToNM = false;
        if (null != nodeLabelsInfo) {
          for (RMNodeLabel label : nodeLabelsInfo) {
            if (label.getLabelName().length() == 0) {
              // Skip DEFAULT_LABEL
              continue;
            }
            if (label.getNumActiveNMs() > 0) {
              hasAnyLabelLinkedToNM = true;
              break;
            }
          }
        }
        if (!hasAnyLabelLinkedToNM) {
          used = sinfo.getUsedCapacity() / 100;
          //label is not enabled in the cluster or there's only "default" label,
          ul.li().
            a(_Q).$style(width(Q_MAX_WIDTH)).
              span().$style(join(width(used), ";left:0%;",
                  used > 1 ? Q_OVER : Q_UNDER))._(".")._().
              span(".q", "Queue: root")._().
            span().$class("qstats").$style(left(Q_STATS_POS)).
              _(join(percent(used), " used"))._().
            _(QueueBlock.class)._();
        } else {
          for (RMNodeLabel label : nodeLabelsInfo) {
            csqinfo.qinfo = null;
            csqinfo.label = label.getLabelName();
            csqinfo.isExclusiveNodeLabel = label.getIsExclusive();
            String nodeLabelDisplay = csqinfo.label.length() == 0
                ? NodeLabel.DEFAULT_NODE_LABEL_PARTITION : csqinfo.label;
            PartitionQueueCapacitiesInfo capacities = sinfo.getCapacities()
                .getPartitionQueueCapacitiesInfo(csqinfo.label);
            used = capacities.getUsedCapacity() / 100;
            String partitionUiTag =
                "Partition: " + nodeLabelDisplay + " " + label.getResource();
            ul.li().
            a(_Q).$style(width(Q_MAX_WIDTH)).
              span().$style(join(width(used), ";left:0%;",
                  used > 1 ? Q_OVER : Q_UNDER))._(".")._().
              span(".q", partitionUiTag)._().
            span().$class("qstats").$style(left(Q_STATS_POS)).
              _(join(percent(used), " used"))._()._();

            //for the queue hierarchy under label
            UL<Hamlet> underLabel = html.ul("#pq");
            underLabel.li().
            a(_Q).$style(width(Q_MAX_WIDTH)).
              span().$style(join(width(used), ";left:0%;",
                  used > 1 ? Q_OVER : Q_UNDER))._(".")._().
              span(".q", "Queue: root")._().
            span().$class("qstats").$style(left(Q_STATS_POS)).
              _(join(percent(used), " used"))._().
            _(QueueBlock.class)._()._();
          }
        }
      }
      ul._()._().
      script().$type("text/javascript").
          _("$('#cs').hide();")._()._().
      _(RMAppsBlock.class);
      html._(HealthBlock.class);
    }
  }

  public static class HealthBlock extends HtmlBlock {

    final CapacityScheduler cs;

    @Inject
    HealthBlock(ResourceManager rm) {
      cs = (CapacityScheduler) rm.getResourceScheduler();
    }

    @Override
    public void render(HtmlBlock.Block html) {
      SchedulerHealth healthInfo = cs.getSchedulerHealth();
      DIV<Hamlet> div = html.div("#health");
      div.h4("调度器汇总");
      TBODY<TABLE<DIV<Hamlet>>> tbody =
          div.table("#lastrun").thead().$class("ui-widget-header").tr().th()
            .$class("ui-state-default")._("Total Container Allocations(count)")
            ._().th().$class("ui-state-default")
            ._("Total Container Releases(count)")._().th()
            .$class("ui-state-default")
            ._("Total Fulfilled Reservations(count)")._().th()
            .$class("ui-state-default")._("Total Container Preemptions(count)")
            ._()._()._().tbody();
      tbody
        .$class("ui-widget-content")
        .tr()
        .td(
          String.valueOf(cs.getRootQueueMetrics()
            .getAggregateAllocatedContainers()))
        .td(
          String.valueOf(cs.getRootQueueMetrics()
            .getAggegatedReleasedContainers()))
        .td(healthInfo.getAggregateFulFilledReservationsCount().toString())
        .td(healthInfo.getAggregatePreemptionCount().toString())._()._()._();
      div.h4("Last scheduler run");
      tbody =
          div.table("#lastrun").thead().$class("ui-widget-header").tr().th()
            .$class("ui-state-default")._("时间")._().th()
            .$class("ui-state-default")._("分配（数量-资源）")._()
            .th().$class("ui-state-default")._("保留（数量-资源）")
            ._().th().$class("ui-state-default")._("释放（数量-资源）")
            ._()._()._().tbody();
      tbody
        .$class("ui-widget-content")
        .tr()
        .td(Times.format(healthInfo.getLastSchedulerRunTime()))
        .td(
          healthInfo.getAllocationCount().toString() + " - "
              + healthInfo.getResourcesAllocated().toString())
        .td(
          healthInfo.getReservationCount().toString() + " - "
              + healthInfo.getResourcesReserved().toString())
        .td(
          healthInfo.getReleaseCount().toString() + " - "
              + healthInfo.getResourcesReleased().toString())._()._()._();
      Map<String, SchedulerHealth.DetailedInformation> info = new HashMap<>();
      info.put("分配信息", healthInfo.getLastAllocationDetails());
      info.put("保留信息", healthInfo.getLastReservationDetails());
      info.put("释放信息", healthInfo.getLastReleaseDetails());
      info.put("优先信息", healthInfo.getLastPreemptionDetails());

      for (Map.Entry<String, SchedulerHealth.DetailedInformation> entry : info
        .entrySet()) {
        String containerId = "N/A";
        String nodeId = "N/A";
        String queue = "N/A";
        String table = "#" + entry.getKey();
        div.h4("最新 " + entry.getKey());
        tbody =
            div.table(table).thead().$class("ui-widget-header").tr().th()
              .$class("ui-state-default")._("时间")._().th()
              .$class("ui-state-default")._("Container Id")._().th()
              .$class("ui-state-default")._("节点 Id")._().th()
              .$class("ui-state-default")._("队列")._()._()._().tbody();
        SchedulerHealth.DetailedInformation di = entry.getValue();
        if (di.getTimestamp() != 0) {
          containerId = di.getContainerId().toString();
          nodeId = di.getNodeId().toString();
          queue = di.getQueue();
        }
        tbody.$class("ui-widget-content").tr()
          .td(Times.format(di.getTimestamp())).td(containerId).td(nodeId)
          .td(queue)._()._()._();
      }
      div._();
    }
  }

  @Override protected void postHead(Page.HTML<_> html) {
    html.
      style().$type("text/css").
        _("#cs { padding: 0.5em 0 1em 0; margin-bottom: 1em; position: relative }",
          "#cs ul { list-style: none }",
          "#cs a { font-weight: normal; margin: 2px; position: relative }",
          "#cs a span { font-weight: normal; font-size: 80% }",
          "#cs-wrapper .ui-widget-header { padding: 0.2em 0.5em }",
          ".qstats { font-weight: normal; font-size: 80%; position: absolute }",
          ".qlegend { font-weight: normal; padding: 0 1em; margin: 1em }",
          "table.info tr th {width: 50%}")._(). // to center info table
      script("/static/jt/jquery.jstree.js").
      script().$type("text/javascript").
        _("$(function() {",
          "  $('#cs a span').addClass('ui-corner-all').css('position', 'absolute');",
          "  $('#cs').bind('loaded.jstree', function (e, data) {",
          "    var callback = { call:reopenQueryNodes }",
          "    data.inst.open_node('#pq', callback);",
          "   }).",
          "    jstree({",
          "    core: { animation: 188, html_titles: true },",
          "    plugins: ['themeroller', 'html_data', 'ui'],",
          "    themeroller: { item_open: 'ui-icon-minus',",
          "      item_clsd: 'ui-icon-plus', item_leaf: 'ui-icon-gear'",
          "    }",
          "  });",
          "  $('#cs').bind('select_node.jstree', function(e, data) {",
          "    var q = $('.q', data.rslt.obj).first().text();",
          "    if (q == 'Queue: root') q = '';",
          "    else {",
          "      q = q.substr(q.lastIndexOf(':') + 2);",
          "      q = '^' + q.substr(q.lastIndexOf('.') + 1) + '$';",
          "    }",
          "    $('#apps').dataTable().fnFilter(q, 4, true);",
          "  });",
          "  $('#cs').show();",
          "});")._().
      _(SchedulerPageUtil.QueueBlockUtil.class);
  }

  @Override protected Class<? extends SubView> content() {
    return QueuesBlock.class;
  }

  static String percent(float f) {
    return String.format("%.1f%%", f * 100);
  }

  static String width(float f) {
    return String.format("width:%.1f%%", f * 100);
  }

  static String left(float f) {
    return String.format("left:%.1f%%", f * 100);
  }
}
