package com.byd.wjx.xiaozhi.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaozhiMessageCodecTest {
    @Test
    fun `hello advertises the actual 16k mono 60ms input format`() {
        val hello = JSONObject(XiaozhiMessageCodec.hello())
        val params = hello.getJSONObject("audio_params")

        assertEquals("hello", hello.getString("type"))
        assertEquals(1, hello.getInt("version"))
        assertEquals("websocket", hello.getString("transport"))
        assertFalse(hello.getJSONObject("features").getBoolean("mcp"))
        assertEquals("opus", params.getString("format"))
        assertEquals(16_000, params.getInt("sample_rate"))
        assertEquals(1, params.getInt("channels"))
        assertEquals(60, params.getInt("frame_duration"))
    }

    @Test
    fun `manual listen messages preserve server session id`() {
        val start = JSONObject(XiaozhiMessageCodec.startListening("session-7"))
        val stop = JSONObject(XiaozhiMessageCodec.stopListening("session-7"))

        assertEquals("session-7", start.getString("session_id"))
        assertEquals("listen", start.getString("type"))
        assertEquals("start", start.getString("state"))
        assertEquals("manual", start.getString("mode"))
        assertEquals("stop", stop.getString("state"))
    }

    @Test
    fun `wake conversation requests server side automatic voice activity detection`() {
        val start = JSONObject(
            XiaozhiMessageCodec.startListening("wake-session", XiaozhiListeningMode.Auto),
        )

        assertEquals("wake-session", start.getString("session_id"))
        assertEquals("auto", start.getString("mode"))
    }

    @Test
    fun `server hello and transcript events parse safely`() {
        val hello = XiaozhiMessageCodec.parse(
            """{"type":"hello","transport":"websocket","session_id":"abc","audio_params":{"sample_rate":24000,"channels":1,"frame_duration":60}}""",
        ) as XiaozhiServerEvent.Hello
        val stt = XiaozhiMessageCodec.parse("""{"type":"stt","text":"打开空调"}""") as XiaozhiServerEvent.Stt
        val tts = XiaozhiMessageCodec.parse(
            """{"type":"tts","state":"sentence_start","text":"好的"}""",
        ) as XiaozhiServerEvent.Tts

        assertEquals("abc", hello.sessionId)
        assertEquals(24_000, hello.sampleRate)
        assertEquals("打开空调", stt.text)
        assertEquals("sentence_start", tts.state)
        assertEquals("好的", tts.text)
        assertTrue(tts.text!!.isNotBlank())
    }
}
