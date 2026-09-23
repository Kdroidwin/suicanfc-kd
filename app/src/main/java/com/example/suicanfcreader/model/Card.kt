package com.example.suicanfcreader.model

import android.content.Context
import com.example.suicanfcreader.lib.SuicaReader

data class Card(
    var cardId: String? = null,
    var date: String? = null,
    var inGatePassageTime: String? = null,
    var outGatePassageTime: String? = null,
    var number: String? = null,
    var payment: String? = null,
    var amount: String? = null,
    var kind: String? = null,
    var device: String? = null,
    var action: String? = null,
    var inLine: String? = null,
    var inStation: String? = null,
    var outLine: String? = null,
    var outStation: String? = null,
    var balance: String? = null,
    var inCompany: String? = null,
    var outCompany: String? = null,
    var memo: String? = null,
    var tags: String? = null,
    var internalCode: String? = null,
    var manuallyEdited: Boolean = false
) {
    companion object {
        fun getCard(context: Context?, felica: SuicaReader): Card {
            val stationPair = if (felica.isStationRecord()) {
                context?.let {
                    Station.resolvePair(
                        context = it,
                        regionCode = felica.regionCode,
                        inLineCode = felica.inLine,
                        inStationCode = felica.inStation,
                        outLineCode = felica.outLine,
                        outStationCode = felica.outStation
                    )
                }
            } else {
                null
            }
            val chargeStationDetails = if (felica.isChargeRecord()) {
                context?.let { Station.getStation(it, felica.regionCode, felica.inLine, felica.inStation) }
            } else {
                null
            }
            val busStopDetails = if (felica.isBusRecord() && !felica.isChargeRecord() && !felica.isShoppingRecord()) {
                context?.let { Station.getBusStop(it, felica.busLine, felica.busStop) }
            } else {
                null
            }
            val inStationDetails = stationPair?.first
            val outStationDetails = stationPair?.second
            // SF history identifies a merchant only for some Suica C7/C8 records.  The same
            // numeric code is reused by other transit-card brands, so never use it for them.
            val merchantDetails = if (felica.isShoppingRecord()) {
                context?.let { Merchant.getSuicaMerchant(it, felica.termId, felica.busStop) }
            } else {
                null
            }

            return Card().apply {
                date = "%04d/%02d/%02d".format(2000 + felica.year, felica.month, felica.day)
                number = felica.seqNo.toString()
                payment = ""
                kind = felica.kind
                device = felica.device
                action = felica.action
                inLine = merchantDetails?.store ?: inStationDetails?.lineName ?: chargeStationDetails?.lineName ?: busStopDetails?.lineName
                inStation = merchantDetails?.detail ?: inStationDetails?.stationName ?: chargeStationDetails?.stationName ?: busStopDetails?.stationName
                inCompany = merchantDetails?.brand ?: inStationDetails?.company ?: chargeStationDetails?.company ?: busStopDetails?.company
                outLine = outStationDetails?.lineName
                outStation = outStationDetails?.stationName
                outCompany = outStationDetails?.company
                balance = felica.remain.toString()
                internalCode = if (felica.isShoppingRecord()) {
                    "Terminal=%02X Shop=%04X".format(felica.termId, felica.busStop)
                } else if (felica.isBusRecord() && !felica.isChargeRecord() && !felica.isShoppingRecord()) {
                    "Bus=%04X Stop=%04X".format(felica.busLine, felica.busStop)
                } else {
                    "Area=%d In=%d/%d Out=%d/%d".format(
                        felica.regionCode,
                        felica.inLine,
                        felica.inStation,
                        felica.outLine,
                        felica.outStation
                    )
                }
            }
        }
    }
}
