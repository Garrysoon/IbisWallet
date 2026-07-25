package github.aeonbtc.ibiswallet.tor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoltzTorRelayTest : FunSpec({

    test("detects websocket upgrade with CRLF headers") {
        val headers =
            "GET /api/v2/ws HTTP/1.1\r\n" +
                "Host: 127.0.0.1:1234\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "\r\n"
        isBoltzTorRelayWebSocketUpgrade(headers) shouldBe true
    }

    test("detects websocket upgrade case-insensitively") {
        val headers =
            "GET /api/v2/ws HTTP/1.1\r\n" +
                "Upgrade: WebSocket\r\n" +
                "\r\n"
        isBoltzTorRelayWebSocketUpgrade(headers) shouldBe true
    }

    test("does not treat plain http as websocket") {
        val headers =
            "POST /api/v2/swap/reverse HTTP/1.1\r\n" +
                "Host: 127.0.0.1:1234\r\n" +
                "Content-Length: 12\r\n" +
                "\r\n"
        isBoltzTorRelayWebSocketUpgrade(headers) shouldBe false
    }

    test("splits crlf header lines without trailing carriage returns") {
        val headers = "Upgrade: websocket\r\nHost: example\r\n\r\n"
        boltzTorRelayHeaderLines(headers) shouldBe listOf("Upgrade: websocket", "Host: example")
    }
})
