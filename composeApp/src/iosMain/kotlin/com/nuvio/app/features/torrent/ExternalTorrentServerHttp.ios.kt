@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nuvio.app.features.torrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.setHTTPMethod
import platform.Foundation.NSData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSMutableData
import platform.Foundation.appendData
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject

private data class HttpResponse(
    val data: NSData?,
    val response: NSURLResponse?
)

private class SimpleHttpDelegate(
    private val completion: CompletableDeferred<HttpResponse>
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val responseData = NSMutableData()
    private var urlResponse: NSURLResponse? = null

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (platform.Foundation.NSURLSessionResponseDisposition) -> Unit
    ) {
        urlResponse = didReceiveResponse
        completionHandler(platform.Foundation.NSURLSessionResponseAllow)
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData
    ) {
        responseData.appendData(didReceiveData)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?
    ) {
        if (didCompleteWithError != null) {
            completion.complete(HttpResponse(null, null))
        } else {
            completion.complete(HttpResponse(responseData, urlResponse))
        }
    }
}

actual object ExternalTorrentServerHttp {

    actual suspend fun fetchStreamUrl(requestUrl: String): String? {
        val url = NSURL(string = requestUrl)
        val nativeRequest = NSMutableURLRequest(
            uRL = url,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = 30.0,
        )
        nativeRequest.setHTTPMethod("GET")

        val completion = CompletableDeferred<HttpResponse>()
        val delegate = SimpleHttpDelegate(completion)
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
            timeoutIntervalForRequest = 30.0
            timeoutIntervalForResource = 60.0
        }
        val session = NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = delegate,
            delegateQueue = NSOperationQueue.mainQueue,
        )
        val task = session.dataTaskWithRequest(nativeRequest)
        task.resume()

        val result = completion.await()
        session.finishTasksAndInvalidate()

        val response = result.response ?: return null
        val data = result.data

        val httpResponse = response as? NSHTTPURLResponse
        val statusCode = httpResponse?.statusCode?.toInt() ?: 0

        if (statusCode in 300..399) {
            return httpResponse?.valueForHTTPHeaderField("Location")
        } else if (statusCode in 200..299) {
            if (data != null && data.length > 0UL) {
                val bytes = data.bytes?.let { pointer ->
                    kotlinx.cinterop.readBytes(pointer, data.length.toInt())
                }
                val bodyStr = bytes?.decodeToString()?.trim()

                if (bodyStr != null && (bodyStr.startsWith("http://") || bodyStr.startsWith("https://"))) {
                    return bodyStr
                }
            }
            return httpResponse?.URL?.absoluteString ?: requestUrl
        }
        return null
    }

    actual suspend fun testReachability(baseUrl: String): Boolean {
        val url = NSURL(string = baseUrl)
        val nativeRequest = NSMutableURLRequest(
            uRL = url,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = 10.0,
        )
        nativeRequest.setHTTPMethod("HEAD")

        val completion = CompletableDeferred<HttpResponse>()
        val delegate = SimpleHttpDelegate(completion)
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
            timeoutIntervalForRequest = 10.0
            timeoutIntervalForResource = 15.0
        }
        val session = NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = delegate,
            delegateQueue = NSOperationQueue.mainQueue,
        )
        val task = session.dataTaskWithRequest(nativeRequest)
        task.resume()

        val result = completion.await()
        session.finishTasksAndInvalidate()

        val response = result.response ?: return false
        val httpResponse = response as? NSHTTPURLResponse
        val statusCode = httpResponse?.statusCode?.toInt() ?: 0
        return statusCode in 200..499
    }
}
