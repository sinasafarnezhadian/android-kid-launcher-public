package net.sinas.kidlauncher

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse

class MyCallScreeningService : CallScreeningService() {

    // TODO: durch echte Whitelist ersetzen (z. B. aus SharedPreferences, von Eltern gepflegt)
    private val whitelist = setOf(
        "+491701234567",
        "+491709999999"
    )

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val response = CallResponse.Builder()

        if (number !in whitelist) {
            response.setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
        }

        respondToCall(callDetails, response.build())
    }
}
