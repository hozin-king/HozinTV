package com.hozinking.hozintv

/** Holder in-memory untuk daftar channel aktif (menghindari TransactionTooLargeException). */
object ChannelHolder {
    var channels: List<com.hozinking.hozintv.model.Channel> = emptyList()
}
