package com.easyacc.hutch.core;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.Hutch;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 19:14 */
class MessageTest {
  String json = """
      {"body":"this is body","code":12,"is_success":true}
      """.trim();

  String jrecord = """
      {"body":"this is body","code":12,"success":false}
      """.trim();

  @Test
  void toJson() throws JsonProcessingException {
    var m = new Msg("this is body", 12, true);
    var j = Hutch.om().writeValueAsString(m);
    assertThat(j).isEqualTo(json);
  }

  @Test
  void toType() {
    var props = new MessageProperties();
    props.setContentType("application/json");
    var msg = new Message(json.getBytes(), props);
    var s = msg.toType(Msg.class);

    assertThat(s.body).isEqualTo("this is body");
    assertThat(s.code).isEqualTo(12);
    assertThat(s.isSuccess).isTrue();
  }

  @Test
  void toJsonRecord() throws JsonProcessingException {
    var m = new MsgRecord("this is body", 12, true);
    var j = Hutch.om().writeValueAsString(m);
    assertThat(j).isEqualTo(jrecord);
  }

  @Test
  void toTypeRecord() {
    var props = new MessageProperties();
    props.setContentType("application/json");
    var msg = new Message(jrecord.getBytes(), props);
    var s = msg.toType(MsgRecord.class);

    assertThat(s.body()).isEqualTo("this is body");
    assertThat(s.code()).isEqualTo(12);
    // 注意, 不要使用 isSuccess, jackson 无法正确识别对应的 getter/setter
    assertThat(s.success()).isFalse();
  }
}
