package com.dingdangmaoup.resin.pura.resin.configuration;

import com.dingdangmaoup.resin.pura.ResinModelBase;
import com.dingdangmaoup.resin.pura.resin.WebApp;
import com.intellij.javaee.appServers.deployment.DeploymentStatus;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

public interface JmxConfigurationStrategy {

  boolean deployWithJmx(ResinModelBase resinModel, WebApp webApp);

  boolean undeployWithJmx(ResinModelBase resinModel, WebApp webApp);

  @NotNull
  DeploymentStatus getDeployStateWithJmx(ResinModelBase resinModel, WebApp webApp, Ref<Boolean> isFinal);
}
