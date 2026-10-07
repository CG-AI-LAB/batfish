package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import javax.annotation.Nonnull;
import org.batfish.datamodel.LineAction;

/**
 * A filter attached to a contract subject ({@code vzRsSubjFiltAtt} or {@code vzRsFiltAtt}), with
 * its action and priority override.
 */
public final class FilterRef implements Serializable {

  public FilterRef(NamedRef filter, LineAction action, String priorityOverride) {
    _filter = filter;
    _action = action;
    _priorityOverride = priorityOverride;
  }

  public @Nonnull NamedRef getFilter() {
    return _filter;
  }

  /** {@code action}: permit (the default) or deny. */
  public @Nonnull LineAction getAction() {
    return _action;
  }

  /**
   * {@code priorityOverride}: {@code default}, {@code level1}, {@code level2} or {@code level3}.
   */
  public @Nonnull String getPriorityOverride() {
    return _priorityOverride;
  }

  private final @Nonnull NamedRef _filter;
  private final @Nonnull LineAction _action;
  private final @Nonnull String _priorityOverride;
}
