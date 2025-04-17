package com.dingdangmaoup.resin.pura.resin.common;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.jetbrains.annotations.NonNls;

public final class MBeanUtil {

  private MBeanUtil() {

  }

  public static ObjectName newObjectName(@NonNls String objectName) {
    try {
      return ObjectName.getInstance(objectName);
    }
    catch (MalformedObjectNameException e) {
      throw new InternalError("Never happens");
    }
  }
}
