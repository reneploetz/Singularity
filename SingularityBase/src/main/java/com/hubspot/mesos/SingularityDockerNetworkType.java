package com.hubspot.mesos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public enum SingularityDockerNetworkType {
  BRIDGE,
  HOST,
  NONE
}
