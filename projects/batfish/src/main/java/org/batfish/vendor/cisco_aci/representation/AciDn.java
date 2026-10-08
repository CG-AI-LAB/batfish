package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Helpers for APIC distinguished names (DNs) and relative names (RNs). */
public final class AciDn {

  /**
   * Splits a DN into its RNs. Slashes inside brackets belong to the RN, as in {@code
   * topology/pod-1/paths-101/pathep-[eth1/5]}.
   */
  public static @Nonnull List<String> split(String dn) {
    ImmutableList.Builder<String> rns = ImmutableList.builder();
    StringBuilder current = new StringBuilder();
    int depth = 0;
    for (int i = 0; i < dn.length(); i++) {
      char ch = dn.charAt(i);
      if (ch == '[') {
        depth++;
      } else if (ch == ']' && depth > 0) {
        depth--;
      }
      if (ch == '/' && depth == 0) {
        rns.add(current.toString());
        current.setLength(0);
      } else {
        current.append(ch);
      }
    }
    rns.add(current.toString());
    return rns.build();
  }

  /**
   * Returns the naming value of the first RN in {@code dn} that starts with {@code prefix}, with
   * any surrounding brackets removed; {@code null} if there is none. For example, prefix {@code
   * "tn-"} in {@code uni/tn-common/BD-default} gives {@code common}.
   */
  public static @Nullable String value(String dn, String prefix) {
    for (String rn : split(dn)) {
      if (rn.startsWith(prefix)) {
        return unbracket(rn.substring(prefix.length()));
      }
    }
    return null;
  }

  /** Returns the parent DN of {@code dn}, or {@code null} if it has none. */
  public static @Nullable String parent(String dn) {
    List<String> rns = split(dn);
    if (rns.size() < 2) {
      return null;
    }
    return String.join("/", rns.subList(0, rns.size() - 1));
  }

  /** Returns the last RN of {@code dn}. */
  public static @Nonnull String lastRn(String dn) {
    List<String> rns = split(dn);
    return rns.get(rns.size() - 1);
  }

  /** Returns the node ID in a {@code topology/pod-N/node-M/...} DN, or {@code null}. */
  public static @Nullable Integer nodeId(String dn) {
    return parseIntOrNull(value(dn, "node-"));
  }

  /** Returns the pod ID in a {@code topology/pod-N/...} DN, or {@code null}. */
  public static @Nullable Integer podId(String dn) {
    return parseIntOrNull(value(dn, "pod-"));
  }

  /** Returns the interface in a {@code .../if-[eth1/49]/...} DN, or {@code null}. */
  public static @Nullable String interfaceName(String dn) {
    return value(dn, "if-");
  }

  /** Returns {@code value} without one pair of surrounding brackets. */
  public static @Nonnull String unbracket(String value) {
    if (value.length() >= 2 && value.startsWith("[") && value.endsWith("]")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  /**
   * Parses a VLAN encapsulation such as {@code vlan-100}. Returns {@code null} for anything else,
   * including {@code unknown} and VXLAN encapsulations.
   */
  public static @Nullable Integer vlan(@Nullable String encap) {
    if (encap == null) {
      return null;
    }
    Matcher m = VLAN_ENCAP.matcher(encap.trim());
    if (!m.matches()) {
      return null;
    }
    int vlan = Integer.parseInt(m.group(1));
    return vlan >= 1 && vlan <= 4094 ? vlan : null;
  }

  static @Nullable Integer parseIntOrNull(@Nullable String text) {
    if (text == null) {
      return null;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static @Nullable Long parseLongOrNull(@Nullable String text) {
    if (text == null) {
      return null;
    }
    try {
      return Long.parseLong(text.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static final Pattern VLAN_ENCAP = Pattern.compile("vlan-(\\d+)");

  private AciDn() {}
}
