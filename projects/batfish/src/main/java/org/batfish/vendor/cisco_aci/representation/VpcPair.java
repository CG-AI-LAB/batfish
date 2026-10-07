package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;
import javax.annotation.Nonnull;

/** A vPC explicit protection group ({@code fabricExplicitGEp}): two leaves paired for vPC. */
public final class VpcPair implements Serializable {

  public VpcPair(String name, int id, List<Integer> nodeIds) {
    _name = name;
    _id = id;
    _nodeIds = ImmutableList.copyOf(nodeIds);
  }

  public @Nonnull String getName() {
    return _name;
  }

  public int getId() {
    return _id;
  }

  public @Nonnull List<Integer> getNodeIds() {
    return _nodeIds;
  }

  private final @Nonnull String _name;
  private final int _id;
  private final @Nonnull List<Integer> _nodeIds;
}
