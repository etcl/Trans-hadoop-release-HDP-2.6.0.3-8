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

package org.apache.hadoop.yarn.server.nodemanager.webapp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.util.Log4jWarningErrorMetricsAppender;
import org.apache.hadoop.yarn.webapp.YarnWebParams;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet;
import org.apache.hadoop.yarn.webapp.util.WebAppUtils;
import org.apache.hadoop.yarn.webapp.view.HtmlBlock;

import com.google.inject.Inject;

public class NavBlock extends HtmlBlock implements YarnWebParams {

  private Configuration conf;

  @Inject
  public NavBlock(Configuration conf) {
	 this.conf = conf;
  }

  @Override
  protected void render(Block html) {

    boolean addErrorsAndWarningsLink = false;
    Log log = LogFactory.getLog(NMErrorsAndWarningsPage.class);
    if (log instanceof Log4JLogger) {
      Log4jWarningErrorMetricsAppender appender = Log4jWarningErrorMetricsAppender.findAppender();
      if (appender != null) {
        addErrorsAndWarningsLink = true;
      }
    }

    String RMWebAppURL =
        WebAppUtils.getResolvedRMWebAppURLWithScheme(this.conf);
	  Hamlet.UL<Hamlet.DIV<Hamlet>> ul = html
      .div("#nav")
      .h3()._("资源管理器")._()
        .ul()
          .li().a(RMWebAppURL, "主页")._()._()
      .h3()._("节点管理器")._() // TODO: Problem if no header like this
        .ul()
          .li()
            .a(url("node"), "节点信息")._()
          .li()
            .a(url("allApplications"), "应用列表")
            ._()
          .li()
            .a(url("allContainers"), "Containers列表")._()
        ._()
      .h3("Tools")
        .ul()
        .li().a("/conf", "配置")._()
        .li().a("/logs", "本地日志")._()
        .li().a("/stacks", "服务器 stacks")._()
        .li().a("/jmx?qry=Hadoop:*", "服务器监控")._();
    if (addErrorsAndWarningsLink) {
      ul.li().a(url("errors-and-warnings"), "错误及警告")._();
    }
    ul._()._();
  }

}
