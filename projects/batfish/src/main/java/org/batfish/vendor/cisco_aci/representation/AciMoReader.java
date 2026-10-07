package org.batfish.vendor.cisco_aci.representation;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;

/**
 * Reads APIC REST API JSON into {@link AciMo} trees.
 *
 * <p>Accepts the shapes APIC produces: a query response ({@code {"totalCount": ..., "imdata":
 * [...]}}), a single wrapped object as in a configuration export ({@code {"polUni": {...}}}), or a
 * bare list of wrapped objects.
 */
public final class AciMoReader {

  static final String KEY_ATTRIBUTES = "attributes";
  static final String KEY_CHILDREN = "children";
  static final String KEY_IMDATA = "imdata";

  /**
   * Returns the top-level managed objects in {@code root}.
   *
   * @throws IllegalArgumentException if {@code root} is not APIC REST API JSON
   */
  public static @Nonnull List<AciMo> read(JsonNode root) {
    if (root.isArray()) {
      return readList(root);
    }
    if (!root.isObject()) {
      throw new IllegalArgumentException("Expected a JSON object or array");
    }
    JsonNode imdata = root.get(KEY_IMDATA);
    if (imdata != null) {
      if (!imdata.isArray()) {
        throw new IllegalArgumentException("Expected \"imdata\" to be a list");
      }
      return readList(imdata);
    }
    return ImmutableList.of(readWrapped(root));
  }

  private static @Nonnull List<AciMo> readList(JsonNode list) {
    ImmutableList.Builder<AciMo> mos = ImmutableList.builder();
    for (JsonNode element : list) {
      mos.add(readWrapped(element));
    }
    return mos.build();
  }

  /** Reads {@code {"<className>": {"attributes": {...}, "children": [...]}}}. */
  private static @Nonnull AciMo readWrapped(JsonNode wrapper) {
    if (!wrapper.isObject() || wrapper.size() != 1) {
      throw new IllegalArgumentException(
          "Expected an object with a single class-name key, found: " + abbreviate(wrapper));
    }
    Entry<String, JsonNode> entry = wrapper.fields().next();
    String className = entry.getKey();
    JsonNode body = entry.getValue();
    if (!body.isObject()) {
      throw new IllegalArgumentException("Expected an object for class " + className);
    }
    ImmutableMap.Builder<String, String> attributes = ImmutableMap.builder();
    JsonNode attrs = body.get(KEY_ATTRIBUTES);
    if (attrs != null && attrs.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> it = attrs.fields();
      while (it.hasNext()) {
        Entry<String, JsonNode> attr = it.next();
        JsonNode value = attr.getValue();
        if (value.isValueNode()) {
          attributes.put(attr.getKey(), value.asText());
        }
      }
    }
    ImmutableList.Builder<AciMo> children = ImmutableList.builder();
    JsonNode childList = body.get(KEY_CHILDREN);
    if (childList != null && childList.isArray()) {
      for (JsonNode child : childList) {
        children.add(readWrapped(child));
      }
    }
    return new AciMo(className, attributes.buildKeepingLast(), children.build());
  }

  private static String abbreviate(JsonNode node) {
    String text = node.toString();
    return text.length() > 80 ? text.substring(0, 80) + "..." : text;
  }

  private AciMoReader() {}
}
