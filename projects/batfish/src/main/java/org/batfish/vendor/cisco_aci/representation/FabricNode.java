package org.batfish.vendor.cisco_aci.representation;

import java.io.Serializable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.datamodel.Ip;

/**
 * A fabric switch or controller ({@code fabricNode}, {@code fabricNodeIdentP}, {@code topSystem}).
 */
public final class FabricNode implements Serializable {

  /** The node role ({@code role} attribute). */
  public enum Role {
    LEAF,
    SPINE,
    CONTROLLER,
    OTHER;

    static @Nonnull Role fromString(@Nullable String role) {
      if (role == null) {
        return OTHER;
      }
      return switch (role) {
        case "leaf" -> LEAF;
        case "spine" -> SPINE;
        case "controller" -> CONTROLLER;
        default -> OTHER;
      };
    }
  }

  public FabricNode(int id) {
    _id = id;
    _podId = 1;
    _role = Role.OTHER;
  }

  public int getId() {
    return _id;
  }

  public int getPodId() {
    return _podId;
  }

  public void setPodId(int podId) {
    _podId = podId;
  }

  public @Nullable String getName() {
    return _name;
  }

  public void setName(@Nullable String name) {
    _name = name;
  }

  public @Nonnull Role getRole() {
    return _role;
  }

  public void setRole(Role role) {
    _role = role;
  }

  /** The node's tunnel endpoint (TEP) address, from {@code fabricNode} or {@code topSystem}. */
  public @Nullable Ip getTepAddress() {
    return _tepAddress;
  }

  public void setTepAddress(@Nullable Ip tepAddress) {
    _tepAddress = tepAddress;
  }

  private final int _id;
  private int _podId;
  private @Nullable String _name;
  private @Nonnull Role _role;
  private @Nullable Ip _tepAddress;
}
