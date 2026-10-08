package org.batfish.vendor.cisco_aci.representation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import org.batfish.common.util.BatfishObjectMapper;

/**
 * Reads APIC REST API JSON into {@link AciMo} trees, recording the lines each object spans.
 *
 * <p>Accepts the shapes APIC produces: a query response ({@code {"totalCount": ..., "imdata":
 * [...]}}), a single wrapped object as in a configuration export ({@code {"polUni": {...}}}), or a
 * bare list of wrapped objects.
 */
public final class AciMoReader {

  static final String KEY_ATTRIBUTES = "attributes";
  static final String KEY_CHILDREN = "children";
  static final String KEY_IMDATA = "imdata";
  static final String KEY_TOTAL_COUNT = "totalCount";

  /**
   * Returns the top-level managed objects in {@code json}.
   *
   * @throws IOException if {@code json} is not valid JSON
   * @throws IllegalArgumentException if {@code json} is not APIC REST API JSON
   */
  public static @Nonnull List<AciMo> read(String json) throws IOException {
    try (JsonParser p = BatfishObjectMapper.mapper().createParser(json)) {
      JsonToken first = p.nextToken();
      if (first == JsonToken.START_ARRAY) {
        return readList(p);
      }
      if (first != JsonToken.START_OBJECT) {
        throw new IllegalArgumentException("Expected a JSON object or array");
      }
      int line = line(p);
      if (p.nextToken() != JsonToken.FIELD_NAME) {
        throw new IllegalArgumentException("Expected a non-empty JSON object");
      }
      String field = p.currentName();
      if (field.equals(KEY_IMDATA) || field.equals(KEY_TOTAL_COUNT)) {
        return readResponse(p);
      }
      return ImmutableList.of(readWrappedBody(p, line));
    }
  }

  /** Reads the fields of a query response, positioned at its first field name. */
  private static @Nonnull List<AciMo> readResponse(JsonParser p) throws IOException {
    List<AciMo> imdata = ImmutableList.of();
    boolean found = false;
    do {
      String field = p.currentName();
      JsonToken value = p.nextToken();
      if (field.equals(KEY_IMDATA)) {
        if (value != JsonToken.START_ARRAY) {
          throw new IllegalArgumentException("Expected \"imdata\" to be a list");
        }
        imdata = readList(p);
        found = true;
      } else {
        p.skipChildren();
      }
    } while (p.nextToken() == JsonToken.FIELD_NAME);
    if (!found) {
      throw new IllegalArgumentException("Expected \"imdata\" in the APIC response");
    }
    return imdata;
  }

  /** Reads a list of wrapped objects, positioned at its start. */
  private static @Nonnull List<AciMo> readList(JsonParser p) throws IOException {
    ImmutableList.Builder<AciMo> mos = ImmutableList.builder();
    while (p.nextToken() != JsonToken.END_ARRAY) {
      if (p.currentToken() != JsonToken.START_OBJECT) {
        throw new IllegalArgumentException(
            String.format(
                "Expected an object with a single class-name key at line %d, found %s",
                line(p), p.currentToken()));
      }
      int line = line(p);
      if (p.nextToken() != JsonToken.FIELD_NAME) {
        throw new IllegalArgumentException(
            String.format("Expected an object with a single class-name key at line %d", line));
      }
      mos.add(readWrappedBody(p, line));
    }
    return mos.build();
  }

  /**
   * Reads {@code {"<className>": {"attributes": {...}, "children": [...]}}}, positioned at the
   * class name. The object started on {@code line}.
   */
  private static @Nonnull AciMo readWrappedBody(JsonParser p, int line) throws IOException {
    String className = p.currentName();
    if (p.nextToken() != JsonToken.START_OBJECT) {
      throw new IllegalArgumentException(
          String.format("Expected an object for class %s at line %d", className, line));
    }
    ImmutableMap.Builder<String, String> attributes = ImmutableMap.builder();
    ImmutableList.Builder<AciMo> children = ImmutableList.builder();
    while (p.nextToken() == JsonToken.FIELD_NAME) {
      String key = p.currentName();
      JsonToken value = p.nextToken();
      if (key.equals(KEY_ATTRIBUTES) && value == JsonToken.START_OBJECT) {
        readAttributes(p, attributes);
      } else if (key.equals(KEY_CHILDREN) && value == JsonToken.START_ARRAY) {
        children.addAll(readList(p));
      } else {
        p.skipChildren();
      }
    }
    if (p.nextToken() != JsonToken.END_OBJECT) {
      throw new IllegalArgumentException(
          String.format(
              "Expected an object with a single class-name key at line %d, found another key"
                  + " after %s",
              line, className));
    }
    return new AciMo(className, attributes.buildKeepingLast(), children.build(), line, line(p));
  }

  /** Reads scalar attributes, positioned at the start of the attributes object. */
  private static void readAttributes(JsonParser p, ImmutableMap.Builder<String, String> attributes)
      throws IOException {
    while (p.nextToken() == JsonToken.FIELD_NAME) {
      String name = p.currentName();
      JsonToken value = p.nextToken();
      if (value.isScalarValue() && value != JsonToken.VALUE_NULL) {
        attributes.put(name, p.getText());
      } else {
        p.skipChildren();
      }
    }
  }

  private static int line(JsonParser p) {
    return p.currentTokenLocation().getLineNr();
  }

  private AciMoReader() {}
}
