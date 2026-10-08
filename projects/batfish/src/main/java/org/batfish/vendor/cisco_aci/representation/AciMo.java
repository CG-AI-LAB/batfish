package org.batfish.vendor.cisco_aci.representation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A generic APIC managed object (MO), as it appears in APIC REST API JSON: a class name such as
 * {@code fvTenant}, its attributes, and its child objects.
 */
public final class AciMo {

  public AciMo(
      String className,
      Map<String, String> attributes,
      List<AciMo> children,
      int line,
      int lastLine) {
    _className = className;
    _attributes = ImmutableMap.copyOf(attributes);
    _children = ImmutableList.copyOf(children);
    _line = line;
    _lastLine = lastLine;
  }

  /** The class name without a package separator, e.g. {@code fvTenant}. */
  public @Nonnull String getClassName() {
    return _className;
  }

  public @Nonnull Map<String, String> getAttributes() {
    return _attributes;
  }

  /** Returns the attribute, or {@code null} if it is absent or empty. */
  public @Nullable String getAttribute(String name) {
    String value = _attributes.get(name);
    return value == null || value.isEmpty() ? null : value;
  }

  /** Returns the attribute, or {@code defaultValue} if it is absent or empty. */
  public @Nonnull String getAttribute(String name, String defaultValue) {
    String value = getAttribute(name);
    return value == null ? defaultValue : value;
  }

  public @Nonnull List<AciMo> getChildren() {
    return _children;
  }

  /** Returns the children of the given class, in order. */
  public @Nonnull List<AciMo> getChildren(String className) {
    return _children.stream()
        .filter(c -> c.getClassName().equals(className))
        .collect(ImmutableList.toImmutableList());
  }

  /** Returns the first child of the given class, if any. */
  public @Nonnull Optional<AciMo> getChild(String className) {
    return _children.stream().filter(c -> c.getClassName().equals(className)).findFirst();
  }

  /** The line of its source file where this object starts. */
  public int getLine() {
    return _line;
  }

  /** The line of its source file where this object, including its children, ends. */
  public int getLastLine() {
    return _lastLine;
  }

  @Override
  public String toString() {
    return _className + _attributes;
  }

  private final @Nonnull String _className;
  private final @Nonnull Map<String, String> _attributes;
  private final @Nonnull List<AciMo> _children;
  private final int _line;
  private final int _lastLine;
}
