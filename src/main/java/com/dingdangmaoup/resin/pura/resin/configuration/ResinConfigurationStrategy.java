package com.dingdangmaoup.resin.pura.resin.configuration;

import com.dingdangmaoup.resin.pura.ResinModel;
import com.dingdangmaoup.resin.pura.resin.ResinInstallation;
import com.dingdangmaoup.resin.pura.resin.WebApp;
import com.intellij.execution.ExecutionException;
import java.io.InputStream;
import org.jdom.Element;

public abstract class ResinConfigurationStrategy {
  private Element myElement;

  public void init(ResinModel serverModel, Element element) throws ExecutionException {
    myElement = element;
  }

  protected final Element getElement() {
    return myElement;
  }

  public void save() throws ExecutionException {

  }

  public abstract void setPort(int port);

  public abstract boolean deploy(WebApp webApp) throws ExecutionException;

  public abstract boolean undeploy(WebApp webApp) throws ExecutionException;

  public abstract InputStream getDefaultResinConfContent();

  public String getServerId() {
    return null;
  }

  public static ResinConfigurationStrategy getForInstallation(final ResinInstallation installation) {
    String verNumber = installation.getVersion().getVersionNumber();
    //Extract only first version number (to accept 2.x, 3.x, 3.1.13, ...)
    int resinVersion = Integer.parseInt(verNumber.substring(0, verNumber.indexOf('.')));
    int buildVersion = Integer.parseInt(verNumber.substring(2, 3));
    return switch (resinVersion) {
      case 2 -> new Resin2XConfigurationStrategy();
      case 3 ->
        //From resin 3.2.0 resin.conf is not longer valid... instead resin.xml
        switch (buildVersion) {
          case 0 -> new Resin3XConfigurationStrategy(installation);
          case 1 -> new Resin31ConfigurationStrategy(installation);
          default -> new ResinXmlConfigurationStrategy(installation);
        };
      case 4 ->
        //3.2 branch was renamed to 4.0 with some incompatible resin.xml changes
        new Resin4XmlConfigurationStrategy(installation);
      default -> new Resin3XConfigurationStrategy(installation);
    };
  }
}

