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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.util.Log4jWarningErrorMetricsAppender;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.DIV;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.LI;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.UL;
import org.apache.hadoop.yarn.webapp.view.HtmlBlock;

public class NavBlock extends HtmlBlock {

  @Override public void render(Block html) {
    boolean addErrorsAndWarningsLink = false;
    Log log = LogFactory.getLog(RMErrorsAndWarningsPage.class);
    if (log instanceof Log4JLogger) {
      Log4jWarningErrorMetricsAppender appender =
          Log4jWarningErrorMetricsAppender.findAppender();
      if (appender != null) {
        addErrorsAndWarningsLink = true;
      }
    }
    UL<DIV<Hamlet>> mainList = html.
      div("#nav").
        h3("集群").
        ul().
          li().a(url("cluster"), "关于")._().
          li().a(url("nodes"), "节点")._().
          li().a(url("nodelabels"), "节点标签")._();
    UL<LI<UL<DIV<Hamlet>>>> subAppsList = mainList.
          li().a(url("apps"), "任务信息").
            ul();
    subAppsList.li()._();
    for (YarnApplicationState state : YarnApplicationState.values()) {
      String stateLabel = "";
       switch (state.toString()){
         case "NEW": stateLabel = "新建";break;
         case "NEW_SAVING": stateLabel = "保存中";break;
         case "SUBMITTED": stateLabel = "已提交";break;
         case "ACCEPTED": stateLabel = "已接受";break;
         case "RUNNING": stateLabel = "运行中";break;
         case "FINISHED": stateLabel = "已完成";break;
         case "FAILED": stateLabel = "已失败";break;
         case "KILLED": stateLabel = "已停止";break;
         default:stateLabel = state.toString();break;
       }
      subAppsList.
              li().a(url("apps", state.toString()), stateLabel)._();
    }
    subAppsList._()._();
    UL<DIV<Hamlet>> tools = mainList.
          li().a(url("scheduler"), "调度器")._()._().
        h3("工具").ul();
        tools.li().a("/conf", "配置")._().
              li().a("/logs", "本地日志")._().
              li().a("/stacks", "服务器stacks")._().
              li().a("/jmx?qry=Hadoop:*", "服务器监视")._();

    if (addErrorsAndWarningsLink) {
      tools.li().a(url("errors-and-warnings"), "错误及警告")._();
    }
    tools._()._();
  }
}
