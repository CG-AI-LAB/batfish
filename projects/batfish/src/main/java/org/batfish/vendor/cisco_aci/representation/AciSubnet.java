package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import javax.annotation.Nonnull;
import org.batfish.datamodel.ConcreteInterfaceAddress;
import org.batfish.datamodel.Prefix;

/**
 * A bridge-domain or EPG subnet ({@code fvSubnet}). Its {@code ip} is the gateway address with the
 * subnet mask, such as {@code 10.1.1.1/24}.
 */
public final class AciSubnet implements Serializable {

  public AciSubnet(ConcreteInterfaceAddress gateway) {
    _gateway = gateway;
  }

  /** The gateway address and mask. */
  public @Nonnull ConcreteInterfaceAddress getGateway() {
    return _gateway;
  }

  public @Nonnull Prefix getPrefix() {
    return _gateway.getPrefix();
  }

  /** {@code scope} contains {@code public}: advertised out of associated L3Outs. */
  public boolean isPublic() {
    return _public;
  }

  public void setPublic(boolean isPublic) {
    _public = isPublic;
  }

  /** {@code scope} contains {@code shared}: leaked to other VRFs by shared-service contracts. */
  public boolean isShared() {
    return _shared;
  }

  public void setShared(boolean shared) {
    _shared = shared;
  }

  /** {@code preferred=yes}: the primary gateway address when a BD has several. */
  public boolean isPreferred() {
    return _preferred;
  }

  public void setPreferred(boolean preferred) {
    _preferred = preferred;
  }

  /** {@code ctrl} contains {@code no-default-gateway}: no gateway address on the leaves. */
  public boolean isNoDefaultGateway() {
    return _noDefaultGateway;
  }

  public void setNoDefaultGateway(boolean noDefaultGateway) {
    _noDefaultGateway = noDefaultGateway;
  }

  private final @Nonnull ConcreteInterfaceAddress _gateway;
  private boolean _public;
  private boolean _shared;
  private boolean _preferred;
  private boolean _noDefaultGateway;
}
