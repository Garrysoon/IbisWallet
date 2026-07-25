package github.aeonbtc.ibiswallet.tor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Regression tests for the loopback Boltz relay.
 *
 * The reverse-swap failure these cover: LWK creates the swap over REST, subscribes
 * on the WebSocket, then blocks waiting for Boltz's first push. Boltz pushes with
 * arbitrarily long idle gaps and LWK only receives, so a short join timeout in the
 * relay closed both sockets ~1s after the upgrade. LWK then timed out and threw the
 * already-created swap away.
 *
 * These use a fake upstream and [BoltzTorRelay.Mode.CLEARNET] pointed at loopback so
 * no real network or TLS is involved.
 */
class BoltzTorRelayWebSocketTest : FunSpec({

    /**
     * Reads one HTTP header block (terminated by CRLFCRLF) without consuming body bytes.
     */
    fun readHeaderBlock(socket: Socket): String {
        val input = socket.getInputStream()
        val builder = StringBuilder()
        var matched = 0
        while (matched < 4) {
            val next = input.read()
            if (next == -1) break
            val ch = next.toChar()
            builder.append(ch)
            matched =
                when {
                    (matched == 0 || matched == 2) && ch == '\r' -> matched + 1
                    (matched == 1 || matched == 3) && ch == '\n' -> matched + 1
                    ch == '\r' -> 1
                    else -> 0
                }
        }
        return builder.toString()
    }

    fun writeAscii(output: OutputStream, text: String) {
        output.write(text.toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()
    }

    test("keeps an upgraded websocket open through a long idle gap") {
        val upstreamReady = CountDownLatch(1)
        val receivedHostHeader = arrayOfNulls<String>(1)

        val upstream = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val upstreamThread = thread(isDaemon = true) {
            runCatching {
                upstream.accept().use { peer ->
                    val headers = readHeaderBlock(peer)
                    receivedHostHeader[0] =
                        headers.split("\r\n").firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                    // Complete the upgrade.
                    writeAscii(
                        peer.getOutputStream(),
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "\r\n",
                    )
                    upstreamReady.countDown()
                    // Simulate Boltz staying silent, then pushing a swap update.
                    // Longer than the relay's plain-HTTP join timeout (1s).
                    Thread.sleep(2_500)
                    writeAscii(peer.getOutputStream(), "swap.created\n")
                    Thread.sleep(1_000)
                }
            }
        }

        val relay =
            BoltzTorRelay(
                mode = BoltzTorRelay.Mode.CLEARNET,
                clearnetHostOverride = "127.0.0.1",
                clearnetPortOverride = upstream.localPort,
                useTlsUpstream = false,
            )

        try {
            val apiUrl = relay.start()
            val port = apiUrl.substringAfterLast(':').substringBefore('/').toInt()

            Socket().use { client ->
                client.connect(InetSocketAddress("127.0.0.1", port), 5_000)
                client.soTimeout = 15_000
                writeAscii(
                    client.getOutputStream(),
                    "GET /v2/ws HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:$port\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "\r\n",
                )

                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1))
                reader.readLine() shouldBe "HTTP/1.1 101 Switching Protocols"

                upstreamReady.await(10, TimeUnit.SECONDS) shouldBe true

                // Drain the remaining upgrade headers.
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                // The push arrives ~2.5s after the upgrade. Before the fix the relay had
                // already closed both sockets and this read returned null (EOF).
                reader.readLine() shouldBe "swap.created"
            }

            receivedHostHeader[0] shouldBe "Host: 127.0.0.1"
        } finally {
            relay.stop()
            runCatching { upstream.close() }
            upstreamThread.join(2_000)
        }
    }

    test("proxies a plain http request and response") {
        val upstream = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val upstreamThread = thread(isDaemon = true) {
            runCatching {
                upstream.accept().use { peer ->
                    readHeaderBlock(peer)
                    val body = "{\"id\":\"swap\"}"
                    writeAscii(
                        peer.getOutputStream(),
                        "HTTP/1.1 201 Created\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${body.length}\r\n" +
                            "\r\n" +
                            body,
                    )
                    Thread.sleep(200)
                }
            }
        }

        val relay =
            BoltzTorRelay(
                mode = BoltzTorRelay.Mode.CLEARNET,
                clearnetHostOverride = "127.0.0.1",
                clearnetPortOverride = upstream.localPort,
                useTlsUpstream = false,
            )

        try {
            val apiUrl = relay.start()
            val port = apiUrl.substringAfterLast(':').substringBefore('/').toInt()

            Socket().use { client ->
                client.connect(InetSocketAddress("127.0.0.1", port), 5_000)
                client.soTimeout = 10_000
                writeAscii(
                    client.getOutputStream(),
                    "GET /v2/swap/reverse HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:$port\r\n" +
                        "\r\n",
                )
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1))
                reader.readLine() shouldBe "HTTP/1.1 201 Created"
            }
        } finally {
            relay.stop()
            runCatching { upstream.close() }
            upstreamThread.join(2_000)
        }
    }

    test("clearnet api url uses the v2 base path") {
        val relay = BoltzTorRelay(mode = BoltzTorRelay.Mode.CLEARNET)
        try {
            relay.start().substringAfter("127.0.0.1:").substringAfter('/') shouldBe "v2"
        } finally {
            relay.stop()
        }
    }

    test("tor api url uses the api v2 base path") {
        val relay = BoltzTorRelay(mode = BoltzTorRelay.Mode.TOR)
        try {
            relay.start().substringAfter("127.0.0.1:").substringAfter('/') shouldBe "api/v2"
        } finally {
            relay.stop()
        }
    }
})
