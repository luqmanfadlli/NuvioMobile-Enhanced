@file:androidx.annotation.OptIn(UnstableApi::class)

package com.nuvio.app.features.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicLong

internal fun DataSource.Factory.countingNetworkBytes(counter: AtomicLong): DataSource.Factory {
    val listener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int,
        ) {
            if (isNetwork) counter.addAndGet(bytesTransferred.toLong())
        }
    }
    val base = this
    return DataSource.Factory {
        base.createDataSource().also { it.addTransferListener(listener) }
    }
}
