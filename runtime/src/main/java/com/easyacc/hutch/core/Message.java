/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.easyacc.hutch.core;

import com.easyacc.hutch.Hutch;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.SneakyThrows;

/**
 * The 0-8 and 0-9-1 AMQP specifications do not define an Message class or interface. Instead, when
 * performing an operation such as basicPublish the content is passed as a byte-array argument and
 * additional properties are passed in as separate arguments. Spring AMQP defines a Message class as
 * part of a more general AMQP domain model representation. The purpose of the Message class is to
 * simply encapsulate the body and properties within a single instance so that the rest of the AMQP
 * API can in turn be simpler.
 *
 * @author Mark Pollack
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Dave Syer
 * @author Gary Russell
 * @author Alex Panchenko
 * @author Artem Bilan
 */
public class Message implements Serializable {

  private static final long serialVersionUID = -7177590352110605597L;

  private static final String DEFAULT_ENCODING = Charset.defaultCharset().name();

  private static final Set<String> ALLOWED_LIST_PATTERNS =
      new LinkedHashSet<>(Arrays.asList("java.util.*", "java.lang.*"));

  private static String bodyEncoding = DEFAULT_ENCODING;

  private final MessageProperties messageProperties;

  private final byte[] body;

  public Message(byte[] body, MessageProperties messageProperties) { // NOSONAR
    this.body = body; // NOSONAR
    this.messageProperties = messageProperties;
  }

  /**
   * Add patterns to the allowed list of permissible package/class name patterns for deserialization
   * in {@link #toString()}. The patterns will be applied in order until a match is found. A class
   * can be fully qualified or a wildcard '*' is allowed at the beginning or end of the class name.
   * Examples: {@code com.foo.*}, {@code *.MyClass}. By default, only {@code java.util} and {@code
   * java.lang} classes will be deserialized.
   *
   * @param patterns the patterns.
   * @since 1.5.7
   */
  public static void addAllowedListPatterns(String... patterns) {
    ALLOWED_LIST_PATTERNS.addAll(Arrays.asList(patterns));
  }

  /**
   * Set the encoding to use in {@link #toString()} when converting the body if there is no {@link
   * MessageProperties#getContentEncoding() contentEncoding} message property present.
   *
   * @param encoding the encoding to use.
   * @since 2.2.4
   */
  public static void setDefaultEncoding(String encoding) {
    bodyEncoding = encoding;
  }

  public byte[] getBody() {
    return this.body; // NOSONAR
  }

  public MessageProperties getMessageProperties() {
    return this.messageProperties;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("(");
    buffer.append("Body:'").append(this.getBodyContentAsString()).append("'");
    if (this.messageProperties != null) {
      buffer.append(" ").append(this.messageProperties.toString());
    }
    buffer.append(")");
    return buffer.toString();
  }

  /**
   * 调整与原来不同的方式, 如果调用 getBodyContentAsString 那么则强制为返回尝试获取 header 头返回 String. 如果 是二进制, 则直接调用原生的
   * getBody
   *
   * @return
   */
  public String getBodyContentAsString() {
    if (this.body == null) {
      return null;
    }
    try {
      boolean nullProps = this.messageProperties == null;
      return new String(this.body, encoding(nullProps));
    } catch (Exception e) {
      // Comes out as '[B@....b' (so harmless)
      return Arrays.toString(this.body) + "(byte[" + this.body.length + "])"; // NOSONAR
    }
  }

  @SneakyThrows
  public <T> T toType(Class<T> clazz) {
    return Hutch.om().readerFor(clazz).readValue(this.getBodyContentAsString());
  }

  private String encoding(boolean nullProps) {
    String encoding = nullProps ? null : this.messageProperties.getContentEncoding();
    if (encoding == null) {
      encoding = bodyEncoding;
    }
    return encoding;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(this.body);
    result =
        prime * result + ((this.messageProperties == null) ? 0 : this.messageProperties.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Message other = (Message) obj;
    if (!Arrays.equals(this.body, other.body)) {
      return false;
    }
    if (this.messageProperties == null) {
      if (other.messageProperties != null) {
        return false;
      }
    } else if (!this.messageProperties.equals(other.messageProperties)) {
      return false;
    }
    return true;
  }
}
