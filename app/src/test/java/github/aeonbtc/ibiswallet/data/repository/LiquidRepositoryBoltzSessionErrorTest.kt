package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiquidRepositoryBoltzSessionErrorTest : FunSpec({

    test("detects dead Boltz session restart signal error") {
        isBoltzSessionDeadSignalError(
            "msg=Invoice failed: BoltzApi(Generic(\"Failed to send restart signal\"))",
        ) shouldBe true
    }

    test("detects restart signal error case-insensitively") {
        isBoltzSessionDeadSignalError("failed to send RESTART signal") shouldBe true
    }

    test("does not match unrelated Boltz errors") {
        isBoltzSessionDeadSignalError("msg=Invoice failed: Timeout(\"request timed out\")") shouldBe false
    }

    test("detects recoverable websocket subscription timeout") {
        isBoltzWsRecoverableErrorMessage("Subscription timeout") shouldBe true
    }

    test("detects recoverable request channel failure") {
        isBoltzWsRecoverableErrorMessage("Failed to send request to channel: Closed") shouldBe true
    }

    test("detects not connected reconnect failure") {
        isBoltzWsRecoverableErrorMessage("Not connected") shouldBe true
    }
})
