package com.hubspot.baragon.service.elb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.google.common.base.Optional;
import com.hubspot.baragon.data.BaragonKnownAgentsDatastore;
import com.hubspot.baragon.data.BaragonLoadBalancerDatastore;
import com.hubspot.baragon.models.BaragonAgentMetadata;
import com.hubspot.baragon.models.BaragonGroup;
import com.hubspot.baragon.models.BaragonKnownAgentMetadata;
import com.hubspot.baragon.service.config.ElbConfiguration;
import com.hubspot.baragon.service.exceptions.BaragonExceptionNotifier;

public abstract class ElasticLoadBalancer {
  protected final Optional<ElbConfiguration> configuration;
  protected final BaragonExceptionNotifier exceptionNotifier;
  protected final BaragonLoadBalancerDatastore loadBalancerDatastore;
  protected final BaragonKnownAgentsDatastore knownAgentsDatastore;

  public ElasticLoadBalancer(Optional<ElbConfiguration> configuration,
                             BaragonExceptionNotifier exceptionNotifier,
                             BaragonLoadBalancerDatastore loadBalancerDatastore,
                             BaragonKnownAgentsDatastore knownAgentsDatastore) {
    this.configuration = configuration;
    this.exceptionNotifier = exceptionNotifier;
    this.loadBalancerDatastore = loadBalancerDatastore;
    this.knownAgentsDatastore  = knownAgentsDatastore;
  }

  abstract public boolean isInstanceHealthy(String instanceId, String name);
  abstract public void removeInstance(Instance instance, String elbName, String agentId);
  abstract public RegisterInstanceResult registerInstance(Instance instance, String elbName, BaragonAgentMetadata agent);
  abstract public void syncAll(Collection<BaragonGroup> groups);

  Optional<BaragonKnownAgentMetadata> knownAgent(BaragonGroup group, Instance instance) {
    Collection<BaragonKnownAgentMetadata> knownAgents = knownAgentsDatastore.getKnownAgentsMetadata(group.getName());
    for (BaragonKnownAgentMetadata agent : knownAgents) {
      if (agent.getEc2().getInstanceId().isPresent() && agent.getEc2().getInstanceId().get().equals(instance.getInstanceId())) {
        return Optional.of(agent);
      }
    }
    return Optional.absent();
  }

  List<String> agentInstanceIds(Collection<BaragonAgentMetadata> agents) {
    List<String> instanceIds = new ArrayList<>();
    for (BaragonAgentMetadata agent : agents) {
      if (agent.getEc2().getInstanceId().isPresent()) {
        instanceIds.add(agent.getEc2().getInstanceId().get());
      } else {
        throw new IllegalArgumentException(String.format("Cannot have an absent Agent Instance Id (agent: %s)", agent.getAgentId()));
      }
    }
    return instanceIds;
  }

  boolean canDeregisterAgent(BaragonGroup group, Instance instance) {
    Optional<BaragonKnownAgentMetadata>  agent = knownAgent(group, instance);
    if (!agent.isPresent()) {
      return true;
    } else {
      if (configuration.get().isRemoveKnownAgentEnabled()) {
        Date lastSeen = new Date(agent.get().getLastSeenAt());
        Date threshold = new Date(System.currentTimeMillis() - (configuration.get().getRemoveKnownAgentMinutes() * 60000L));
        return lastSeen.before(threshold);
      } else {
        return false;
      }
    }
  }
}
